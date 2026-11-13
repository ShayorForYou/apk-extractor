package com.shayor.apkextractor.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shayor.apkextractor.data.ApkInstaller
import com.shayor.apkextractor.data.ExportException
import com.shayor.apkextractor.data.HistoryEntry
import com.shayor.apkextractor.data.InstallResult
import com.shayor.apkextractor.data.PackageToInstall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Immutable
sealed interface InstallState {
    data object Idle : InstallState
    data object Reading : InstallState
    data class Ready(val pkg: PackageToInstall) : InstallState

    /** [progress] is null once the files are written and the system is installing / asking the user. */
    data class Installing(val pkg: PackageToInstall, val progress: Float?, val waitingForUser: Boolean = false) :
        InstallState

    data class Done(val pkg: PackageToInstall, val canOpen: Boolean) : InstallState
    data class Failed(
        val message: String,
        val pkg: PackageToInstall? = null,
        /** Blocked by another copy of the app, which has to be removed first. */
        val conflict: Boolean = false,
    ) : InstallState
}

class InstallViewModel(app: Application) : AndroidViewModel(app) {

    private val installer = ApkInstaller(app)

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val _canInstall = MutableStateFlow(installer.canRequestInstalls())
    val canInstall: StateFlow<Boolean> = _canInstall.asStateFlow()

    /** Activities to start (system install prompt, settings, uninstall, the installed app). */
    private val _launch = Channel<Intent>(Channel.BUFFERED)
    val launch: Flow<Intent> = _launch.receiveAsFlow()

    private var job: Job? = null
    private var sessionId = -1
    private var confirmIntent: Intent? = null

    /** The user tapped Install but first had to allow installs; continue when they come back. */
    private var installAfterPermission = false
    private val iconPx = (64 * app.resources.displayMetrics.density).roundToInt()

    init {
        viewModelScope.launch {
            ApkInstaller.results.collect { result ->
                if (result.sessionId != sessionId) return@collect
                val current = _state.value as? InstallState.Installing ?: return@collect
                when (result) {
                    is InstallResult.NeedsConfirmation -> {
                        confirmIntent = result.intent
                        _state.value = current.copy(progress = null, waitingForUser = true)
                        _launch.trySend(result.intent)
                    }
                    is InstallResult.Success -> {
                        finishSession()
                        _state.value = InstallState.Done(current.pkg, installer.launchIntent(current.pkg.packageName) != null)
                    }
                    is InstallResult.Failure -> {
                        finishSession()
                        _state.value = if (result.cancelled) {
                            InstallState.Ready(current.pkg)
                        } else {
                            InstallState.Failed(result.message, current.pkg, result.conflict)
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Opening files
    // ---------------------------------------------------------------------------------------------

    fun open(uri: Uri) {
        if (_state.value is InstallState.Installing) return
        job?.cancel()
        _state.value = InstallState.Reading
        job = viewModelScope.launch {
            _state.value = try {
                InstallState.Ready(installer.read(uri, iconPx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExportException) {
                InstallState.Failed(e.message ?: ApkInstaller.CANT_OPEN)
            } catch (e: Exception) {
                InstallState.Failed(ApkInstaller.NOT_AN_APK)
            }
        }
    }

    fun open(entry: HistoryEntry) = open(Uri.parse(entry.uri))

    /** Files shared or opened with the app: one file, or several split APKs of one app. */
    fun openShared(uris: List<Uri>) {
        if (uris.size == 1) return open(uris[0])
        if (uris.isEmpty() || _state.value is InstallState.Installing) return
        job?.cancel()
        _state.value = InstallState.Reading
        job = viewModelScope.launch {
            _state.value = try {
                InstallState.Ready(installer.readMany(uris, iconPx))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExportException) {
                InstallState.Failed(e.message ?: ApkInstaller.CANT_OPEN)
            } catch (e: Exception) {
                InstallState.Failed(ApkInstaller.NOT_AN_APK)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Installing
    // ---------------------------------------------------------------------------------------------

    fun refreshPermission() {
        _canInstall.value = installer.canRequestInstalls()
        if (installAfterPermission && _canInstall.value && _state.value is InstallState.Ready) {
            installAfterPermission = false
            install()
            return
        }
        // Back from the uninstall screen: re-check what is on the phone.
        val ready = _state.value as? InstallState.Ready ?: return
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) { installer.withInstalledState(ready.pkg) }
            _state.update { if (it is InstallState.Ready && it.pkg.packageName == fresh.packageName) InstallState.Ready(fresh) else it }
        }
    }

    fun allowInstalls() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        _launch.trySend(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.fromParts("package", getApplication<Application>().packageName, null),
            ),
        )
    }

    fun install() {
        val pkg = (_state.value as? InstallState.Ready)?.pkg ?: return
        if (!installer.canRequestInstalls()) {
            installAfterPermission = true
            allowInstalls()
            return
        }
        _state.value = InstallState.Installing(pkg, 0f)
        job = viewModelScope.launch {
            try {
                var last = 0
                sessionId = installer.install(pkg) { fraction ->
                    val percent = (fraction * 100).toInt()
                    if (percent != last) {
                        last = percent
                        _state.update { s -> if (s is InstallState.Installing) s.copy(progress = fraction) else s }
                    }
                }
                _state.update { s -> if (s is InstallState.Installing && !s.waitingForUser) s.copy(progress = null) else s }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExportException) {
                _state.value = InstallState.Failed(e.message ?: ApkInstaller.CANT_INSTALL, pkg)
            }
        }
    }

    fun retry() {
        val pkg = (_state.value as? InstallState.Failed)?.pkg ?: return
        _state.value = InstallState.Ready(pkg)
        install()
    }

    /** Shows the system install prompt again (e.g. the user backed out of it without answering). */
    fun showPromptAgain() {
        confirmIntent?.let { _launch.trySend(it) }
    }

    /** Stops a pending install and returns to the preview. */
    fun cancelInstall() {
        val current = _state.value as? InstallState.Installing ?: return
        job?.cancel()
        if (sessionId != -1) installer.abandon(sessionId)
        finishSession()
        _state.value = InstallState.Ready(current.pkg)
    }

    fun uninstallInstalled() {
        val pkg = when (val s = _state.value) {
            is InstallState.Ready -> s.pkg
            // Back to Ready, so the check on return from the uninstall screen sees the change.
            is InstallState.Failed -> s.pkg?.also { _state.value = InstallState.Ready(it) }
            else -> null
        } ?: return
        @Suppress("DEPRECATION")
        _launch.trySend(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg.packageName, null)))
    }

    fun openInstalled() {
        val pkg = (_state.value as? InstallState.Done)?.pkg ?: return
        installer.launchIntent(pkg.packageName)?.let { _launch.trySend(it) }
        dismiss()
    }

    fun dismiss() {
        if (_state.value is InstallState.Installing) return // finishes on its own; the sheet stays
        job?.cancel()
        installAfterPermission = false
        _state.value = InstallState.Idle
        viewModelScope.launch(Dispatchers.IO) { installer.clear() }
    }

    private fun finishSession() {
        sessionId = -1
        confirmIntent = null
    }

    override fun onCleared() {
        installer.clear()
    }
}
