package com.shayor.apkextractor.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Apps the user asked to back up automatically. Whenever one of them is installed in a version
 * that hasn't been saved yet, a background job saves it to the configured folder.
 *
 * Android doesn't deliver "app updated" broadcasts to apps that aren't running, so a periodic job
 * catches updates made while APK Extractor is closed; while it is open, updates trigger a job at once.
 */
class AutoBackup private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
    private val _watched = MutableStateFlow(prefs.getStringSet(K_WATCHED, null).orEmpty().toSet())
    val watched: StateFlow<Set<String>> = _watched.asStateFlow()

    /**
     * How each split app is backed up (APK or APKS), as chosen in its details when auto-backup was
     * turned on or since. Apps without an entry (not split, or watched before this existed) follow
     * the Split Apps default in Settings.
     */
    private val _formats = MutableStateFlow(readFormats())
    val formats: StateFlow<Map<String, SplitMode>> = _formats.asStateFlow()

    /** [format]: for a split app, how to save it; null keeps the Settings default. */
    fun set(packageName: String, enabled: Boolean, format: SplitMode? = null) {
        val now = _watched.updateAndGet { if (enabled) it + packageName else it - packageName }
        val keep = format.takeIf { enabled }
        prefs.edit {
            putStringSet(K_WATCHED, now)
            if (!enabled) remove(doneKey(packageName))
            if (keep != null) putString(formatKey(packageName), keep.name) else remove(formatKey(packageName))
        }
        _formats.update { if (keep != null) it + (packageName to keep) else it - packageName }
        schedule()
        if (enabled) runNow()
    }

    /** Changes how a watched split app is saved, from its next new version on. */
    fun setFormat(packageName: String, format: SplitMode) {
        if (packageName !in _watched.value || _formats.value[packageName] == format) return
        prefs.edit { putString(formatKey(packageName), format.name) }
        _formats.update { it + (packageName to format) }
    }

    internal fun formatFor(packageName: String): SplitMode? = _formats.value[packageName]

    private fun readFormats(): Map<String, SplitMode> =
        prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(FORMAT_PREFIX)) return@mapNotNull null
            val mode = SplitMode.entries.firstOrNull { it.name == value } ?: return@mapNotNull null
            key.removePrefix(FORMAT_PREFIX) to mode
        }.toMap()

    /** Called when an app was installed or updated while APK Extractor is running. */
    fun onPackageChanged(packageName: String) {
        if (packageName in _watched.value) runNow()
    }

    /** Keeps the periodic job in place while any app is watched (e.g. after an app update of our own). */
    fun schedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (_watched.value.isEmpty()) {
            scheduler.cancel(PERIODIC_ID)
            return
        }
        if (scheduler.allPendingJobs.any { it.id == PERIODIC_ID }) return
        val job = JobInfo.Builder(PERIODIC_ID, ComponentName(context, AutoBackupJobService::class.java))
            .setPeriodic(PERIOD_MS, FLEX_MS)
            .setPersisted(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 26) {
                    setRequiresBatteryNotLow(true)
                    setRequiresStorageNotLow(true)
                }
            }
            .build()
        runCatching { scheduler.schedule(job) }.onFailure { Log.w(TAG, "Couldn't schedule auto-backup", it) }
    }

    private fun runNow() {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(NOW_ID, ComponentName(context, AutoBackupJobService::class.java))
            .setMinimumLatency(1) // a job needs one constraint; this is the cheapest (no deadline: it adds nothing)
            .build()
        runCatching { scheduler.schedule(job) }.onFailure { Log.w(TAG, "Couldn't start auto-backup", it) }
    }

    internal fun isDue(app: AppInfo) = prefs.getString(doneKey(app.packageName), null) != versionKey(app)

    internal fun markDone(app: AppInfo) = prefs.edit { putString(doneKey(app.packageName), versionKey(app)) }

    private fun doneKey(pkg: String) = "done_$pkg"
    private fun formatKey(pkg: String) = "$FORMAT_PREFIX$pkg"
    private fun versionKey(app: AppInfo) = "${app.versionCode}@${app.lastUpdateTime}"

    companion object {
        private const val TAG = "AutoBackup"
        private const val K_WATCHED = "watched"
        private const val FORMAT_PREFIX = "format_"
        private const val PERIODIC_ID = 4101
        private const val NOW_ID = 4102
        private const val PERIOD_MS = 6 * 60 * 60 * 1000L
        private const val FLEX_MS = 60 * 60 * 1000L

        @Volatile
        private var instance: AutoBackup? = null

        fun get(context: Context): AutoBackup =
            instance ?: synchronized(this) {
                instance ?: AutoBackup(context.applicationContext).also { instance = it }
            }

        private val runLock = Mutex()

        /** Saves every watched app whose installed version hasn't been backed up yet. */
        internal suspend fun runOnce(context: Context) = runLock.withLock {
            val store = get(context)
            val repo = AppRepository(context)
            val exporter = ApkExporter(context)
            val history = HistoryRepository.get(context)
            val defaults = SettingsRepository.get(context).settings.value
            for (pkg in store.watched.value) {
                val app = repo.load(pkg) ?: continue // uninstalled for now; keep watching
                if (!store.isDue(app)) continue
                // This app's own format, if it has one; otherwise the Settings default.
                val settings = store.formatFor(pkg)?.let { defaults.copy(splitMode = it) } ?: defaults
                // Already saved this exact version (e.g. extracted by hand)? Nothing to do.
                val extension = if (app.isSplit && settings.splitMode == SplitMode.BUNDLE) "apks" else "apk"
                val saved = history.findSaved(app, extension)
                if (saved != null && history.exists(saved)) {
                    store.markDone(app)
                    continue
                }
                try {
                    val file = exporter.exportToStorage(app, settings, onStage = {}, onProgress = {})
                    history.add(app, file, settings.outputTreeUri, auto = true)
                    store.markDone(app)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ExportException) {
                    Log.w(TAG, "Auto-backup of $pkg failed: ${e.message}")
                    if (e.message == ApkExporter.FOLDER_LOST) break // every other app would fail too
                }
            }
        }
    }
}

/** Runs [AutoBackup.runOnce] for the periodic and the immediate job. */
class AutoBackupJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            var failed = false
            try {
                AutoBackup.runOnce(applicationContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("AutoBackup", "Auto-backup run failed", e)
                failed = true
            }
            jobFinished(params, failed)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true // try again later
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}
