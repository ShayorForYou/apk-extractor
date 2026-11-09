package com.extractor.ui

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.extractor.data.ApkExporter
import com.extractor.data.AppFilter
import com.extractor.data.AppIcons
import com.extractor.data.AppInfo
import com.extractor.data.AppRepository
import com.extractor.data.ExportException
import com.extractor.data.ExportedFile
import com.extractor.data.HistoryEntry
import com.extractor.data.HistoryRepository
import com.extractor.data.NameFormat
import com.extractor.data.Settings
import com.extractor.data.SettingsRepository
import com.extractor.data.SortBy
import com.extractor.data.SplitMode
import com.extractor.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Collator
import kotlin.math.roundToInt
import java.util.concurrent.atomic.AtomicLong

@Immutable
data class ListState(
    val loading: Boolean = true,
    val error: String? = null,
    val apps: List<AppInfo> = emptyList(),
    val totalBytes: Long = 0,
    val userCount: Int = 0,
    val systemCount: Int = 0,
)

enum class TaskKind { EXTRACT, SHARE }

@Immutable
sealed interface TaskState {
    data object Idle : TaskState

    data class Running(
        val kind: TaskKind,
        val currentLabel: String,
        val currentPackage: String,
        val currentIconKey: String,
        val index: Int,
        val total: Int,
        val bytesDone: Long,
        val bytesTotal: Long,
        /** Non-null while a step without byte progress runs (e.g. merging split APKs). */
        val stage: String? = null,
    ) : TaskState {
        val progress: Float
            get() = if (bytesTotal <= 0) 0f else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
    }
}

/** An app that was extracted, with the file it produced. */
@Immutable
data class SavedApp(val label: String, val packageName: String, val iconKey: String, val file: ExportedFile)

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Launch(val intent: Intent) : UiEvent

    /** Tries each intent in order until one starts; shows [failMessage] if none can. */
    data class LaunchAny(val intents: List<Intent>, val failMessage: String) : UiEvent

    /** Result of an extraction, shown in a sheet with "Open folder" / "Share". */
    data class Extracted(
        val saved: List<SavedApp>,
        val failures: List<Pair<String, String>>,
        val location: String,
        val treeUri: String?,
    ) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository.get(app)
    private val repo = AppRepository(app)
    private val exporter = ApkExporter(app)
    private val historyRepo = HistoryRepository.get(app)

    val history: StateFlow<List<HistoryEntry>> = historyRepo.entries

    val settings: StateFlow<Settings> = settingsRepo.settings

    private val allApps = MutableStateFlow<List<AppInfo>?>(null)
    private val loadError = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _task = MutableStateFlow<TaskState>(TaskState.Idle)
    val task: StateFlow<TaskState> = _task.asStateFlow()

    private val _shareCacheBytes = MutableStateFlow(0L)
    val shareCacheBytes: StateFlow<Long> = _shareCacheBytes.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private var taskJob: Job? = null

    private val _ready = MutableStateFlow(false)

    /** True once the first screen can be drawn complete (apps loaded, first icons decoded). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val listState: StateFlow<ListState> = combine(
        allApps,
        loadError,
        query,
        settings.map { Triple(it.filter, it.sortBy, it.sortAscending) }.distinctUntilChanged(),
    ) { apps, error, q, (filter, sortBy, ascending) ->
        if (apps == null) return@combine ListState(loading = error == null, error = error)
        val needle = q.trim()
        val visible = apps.asSequence()
            .filter {
                when (filter) {
                    AppFilter.USER -> !it.isSystem
                    AppFilter.SYSTEM -> it.isSystem
                    AppFilter.ALL -> true
                }
            }
            .filter {
                needle.isEmpty() ||
                    it.label.contains(needle, ignoreCase = true) ||
                    it.packageName.contains(needle, ignoreCase = true)
            }
            .sortedWith(comparator(sortBy, ascending))
            .toList()
        ListState(
            loading = false,
            error = error,
            apps = visible,
            totalBytes = visible.sumOf { it.sizeBytes },
            userCount = apps.count { !it.isSystem },
            systemCount = apps.count { it.isSystem },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    /** Keeps the list live while the app is open: installs, updates, uninstalls, enable/disable. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            // An update sends REMOVED(replacing) + ADDED + REPLACED; only react to the final state.
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) return
            viewModelScope.launch { refreshPackage(pkg) }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        load()
        prepareFirstScreen()
        verifyOutputFolder()
        viewModelScope.launch { exporter.trimShareCache() }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
    }

    // ---------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------

    private fun load() = viewModelScope.launch {
        try {
            val apps = repo.loadAll()
            allApps.value = apps
            loadError.value = null
            val installed = apps.mapTo(HashSet()) { it.packageName }
            _selection.update { it.filterTo(HashSet()) { p -> p in installed } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadError.value = "Couldn't load apps"
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * Keeps the splash up until the list is ready and the first rows' icons are in the cache,
     * so the home screen appears complete instead of popping in piece by piece.
     */
    private fun prepareFirstScreen() = viewModelScope.launch {
        val app = getApplication<Application>()
        withTimeoutOrNull(FIRST_SCREEN_BUDGET_MS) {
            val first = listState.first { !it.loading }
            val px = (ROW_ICON_DP * app.resources.displayMetrics.density).roundToInt()
            coroutineScope {
                first.apps.take(FIRST_SCREEN_ICONS)
                    .map { async { AppIcons.load(app, it.packageName, it.iconKey, px) } }
                    .awaitAll()
            }
        }
        _ready.value = true
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        load()
    }

    fun retry() {
        loadError.value = null
        load()
    }

    private suspend fun refreshPackage(pkg: String) {
        val info = repo.load(pkg)
        allApps.update { list ->
            list?.let { current ->
                val without = current.filterNot { it.packageName == pkg }
                if (info != null) without + info else without
            }
        }
        if (info == null) _selection.update { it - pkg }
    }

    val appsByPackage: StateFlow<Map<String, AppInfo>> = allApps
        .map { list -> list.orEmpty().associateBy { it.packageName } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---------------------------------------------------------------------------------------------
    // Search / filter / sort / selection
    // ---------------------------------------------------------------------------------------------

    fun setQuery(q: String) {
        query.value = q
    }

    fun setFilter(filter: AppFilter) = settingsRepo.update { it.copy(filter = filter) }

    fun setSort(sortBy: SortBy) = settingsRepo.update {
        // Tapping the active sort flips its direction.
        if (it.sortBy == sortBy) it.copy(sortAscending = !it.sortAscending)
        else it.copy(sortBy = sortBy, sortAscending = sortBy == SortBy.NAME)
    }

    fun toggleSelection(pkg: String) = _selection.update { if (pkg in it) it - pkg else it + pkg }

    fun selectAllVisible() {
        val visible = listState.value.apps.map { it.packageName }
        _selection.update { current ->
            if (current.containsAll(visible)) current - visible.toSet() else current + visible
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    // ---------------------------------------------------------------------------------------------
    // Export / share
    // ---------------------------------------------------------------------------------------------

    /** True when saving to the default folder needs WRITE_EXTERNAL_STORAGE (Android 9 and older). */
    fun needsLegacyStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < 29 &&
            settings.value.outputTreeUri == null &&
            ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED

    /** [splitMode] overrides the Settings choice for this one extraction (null = use Settings). */
    fun extract(packages: Collection<String>, splitMode: SplitMode? = null) = runTask(TaskKind.EXTRACT, packages, splitMode)

    fun share(packages: Collection<String>, splitMode: SplitMode? = null) = runTask(TaskKind.SHARE, packages, splitMode)

    fun cancelTask() {
        taskJob?.cancel()
    }

    private fun runTask(kind: TaskKind, packages: Collection<String>, splitMode: SplitMode? = null) {
        if (_task.value !is TaskState.Idle) return
        val byPkg = allApps.value.orEmpty().associateBy { it.packageName }
        // Keep the list's order so batch progress feels natural.
        val ordered = listState.value.apps.filter { it.packageName in packages }
        val apps = (ordered + packages.mapNotNull { byPkg[it] }).distinctBy { it.packageName }
        if (apps.isEmpty()) {
            _events.trySend(UiEvent.Message("App not found"))
            return
        }

        val settings = settings.value.let { if (splitMode != null) it.copy(splitMode = splitMode) else it }
        fun estimateOf(app: AppInfo) =
            if (settings.splitMode == SplitMode.BASE_ONLY) app.baseSizeBytes else app.sizeBytes
        val estimate = apps.sumOf(::estimateOf)
        val done = AtomicLong(0)
        var completed = 0L
        var lastEmit = 0L

        taskJob = viewModelScope.launch {
            val saved = mutableListOf<ExportedFile>()
            val savedApps = mutableListOf<SavedApp>()
            val failures = mutableListOf<Pair<String, String>>()
            try {
                val shareDir = if (kind == TaskKind.SHARE) exporter.newShareDir() else null
                for ((index, app) in apps.withIndex()) {
                    _task.value = TaskState.Running(
                        kind, app.label, app.packageName, app.iconKey, index, apps.size, done.get(), estimate,
                    )
                    val onProgress: (Long) -> Unit = { n ->
                        val total = done.addAndGet(n)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmit >= PROGRESS_INTERVAL_MS) { // ~30 fps is plenty
                            lastEmit = now
                            _task.update { s ->
                                if (s is TaskState.Running) s.copy(bytesDone = total, bytesTotal = maxOf(estimate, total)) else s
                            }
                        }
                    }
                    val onStage: (String?) -> Unit = { stage ->
                        _task.update { s -> if (s is TaskState.Running) s.copy(stage = stage) else s }
                    }
                    try {
                        val file = if (shareDir != null) {
                            exporter.exportForShare(app, settings, shareDir, onStage, onProgress)
                        } else {
                            exporter.exportToStorage(app, settings, onStage, onProgress).also {
                                historyRepo.add(app, it, settings.outputTreeUri)
                            }
                        }
                        saved += file
                        savedApps += SavedApp(app.label, app.packageName, app.iconKey, file)
                    } catch (e: ExportException) {
                        failures += app.label to (e.message ?: "Unknown error")
                        if (e.message == ApkExporter.FOLDER_LOST) {
                            clearOutputFolder()
                            break // every remaining app would fail the same way
                        }
                    }
                    // Merged APKs are usually smaller than their parts; snap progress to this
                    // app's share of the estimate so the bar stays accurate for the next one.
                    completed += estimateOf(app)
                    done.set(completed)
                }
                report(kind, saved, savedApps, failures, settings)
            } catch (e: CancellationException) {
                _events.trySend(
                    UiEvent.Message(
                        if (saved.isEmpty()) "Cancelled" else "Cancelled. ${saved.size} of ${apps.size} saved",
                    ),
                )
                throw e
            } finally {
                _task.value = TaskState.Idle
                taskJob = null
            }
        }
    }

    private fun report(
        kind: TaskKind,
        saved: List<ExportedFile>,
        savedApps: List<SavedApp>,
        failures: List<Pair<String, String>>,
        settings: Settings,
    ) {
        if (kind == TaskKind.SHARE) {
            if (saved.isNotEmpty()) _events.trySend(UiEvent.Launch(exporter.shareIntent(saved)))
            if (failures.isNotEmpty()) {
                val text = if (failures.size == 1) failures[0].second else "Couldn't share ${failures.size} apps"
                _events.trySend(UiEvent.Message(text))
            }
            return
        }
        clearSelection()
        val location = saved.firstOrNull()?.location ?: settings.outputFolderName ?: ApkExporter.DEFAULT_LOCATION
        _events.trySend(UiEvent.Extracted(savedApps, failures, location, settings.outputTreeUri))
    }

    // ---------------------------------------------------------------------------------------------
    // History / opening saved files
    // ---------------------------------------------------------------------------------------------

    fun openFolder(treeUri: String?) {
        _events.trySend(UiEvent.LaunchAny(HistoryRepository.folderIntents(treeUri), "No file manager found"))
    }

    fun shareFiles(files: List<ExportedFile>) {
        if (files.isNotEmpty()) _events.trySend(UiEvent.Launch(exporter.shareIntent(files)))
    }

    fun openHistoryFolder(entry: HistoryEntry) = viewModelScope.launch {
        if (historyRepo.exists(entry)) openFolder(entry.treeUri) else fileGone(entry)
    }

    fun shareHistory(entry: HistoryEntry) = viewModelScope.launch {
        if (historyRepo.exists(entry)) shareFiles(listOf(entry.file)) else fileGone(entry)
    }

    private fun fileGone(entry: HistoryEntry) {
        historyRepo.remove(entry.id)
        _events.trySend(UiEvent.Message("That file was moved or deleted"))
    }

    fun removeHistory(entry: HistoryEntry) = historyRepo.remove(entry.id)

    fun clearHistory() = historyRepo.clear()

    // ---------------------------------------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = settingsRepo.update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = settingsRepo.update { it.copy(dynamicColor = enabled) }
    fun setAccent(argb: Int?) = settingsRepo.update { it.copy(accent = argb) }
    fun dismissTips() = settingsRepo.update { it.copy(showTips = false) }
    fun setPureBlack(enabled: Boolean) = settingsRepo.update { it.copy(pureBlack = enabled) }
    fun setSplitMode(mode: SplitMode) = settingsRepo.update { it.copy(splitMode = mode) }
    fun setNameFormat(format: NameFormat) = settingsRepo.update { it.copy(nameFormat = format) }

    fun setOutputFolder(tree: Uri) {
        val app = getApplication<Application>()
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            app.contentResolver.takePersistableUriPermission(tree, flags)
        } catch (_: SecurityException) {
            _events.trySend(UiEvent.Message("Can't use that folder. Pick another one."))
            return
        }
        val previous = settings.value.outputTreeUri
        if (previous != null && previous != tree.toString()) releaseTree(previous)
        val name = exporter.folderName(tree) ?: tree.lastPathSegment?.substringAfterLast(':') ?: "Selected folder"
        settingsRepo.update { it.copy(outputTreeUri = tree.toString(), outputFolderName = name) }
    }

    fun resetOutputFolder() {
        settings.value.outputTreeUri?.let(::releaseTree)
        clearOutputFolder()
    }

    private fun clearOutputFolder() {
        settingsRepo.update { it.copy(outputTreeUri = null, outputFolderName = null) }
    }

    /** The user may have revoked access or deleted the folder since last launch. */
    private fun verifyOutputFolder() {
        val tree = settings.value.outputTreeUri ?: return
        if (!exporter.hasTreeAccess(Uri.parse(tree))) {
            clearOutputFolder()
            _events.trySend(
                UiEvent.Message("Save folder reset to ${ApkExporter.DEFAULT_LOCATION}"),
            )
        }
    }

    /** Persisted grants are capped per app (128–512), so release ones we no longer use. */
    private fun releaseTree(uri: String) {
        runCatching {
            getApplication<Application>().contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun refreshShareCacheSize() = viewModelScope.launch {
        _shareCacheBytes.value = exporter.shareCacheSize()
    }

    fun clearShareCache() = viewModelScope.launch {
        if (_task.value is TaskState.Running) return@launch
        exporter.clearShareCache()
        _shareCacheBytes.value = 0
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 33L
        const val FIRST_SCREEN_BUDGET_MS = 1_200L
        const val FIRST_SCREEN_ICONS = 14
        const val ROW_ICON_DP = 46f // matches the list row icon size

        fun comparator(sortBy: SortBy, ascending: Boolean): Comparator<AppInfo> {
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val byName = Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.packageName }
            val primary: Comparator<AppInfo> = when (sortBy) {
                SortBy.NAME -> byName
                SortBy.SIZE -> compareBy { it.sizeBytes }
                SortBy.INSTALLED -> compareBy { it.firstInstallTime }
                SortBy.UPDATED -> compareBy { it.lastUpdateTime }
            }
            val directed = if (ascending) primary else primary.reversed()
            return if (sortBy == SortBy.NAME) directed else directed.then(byName)
        }
    }
}
