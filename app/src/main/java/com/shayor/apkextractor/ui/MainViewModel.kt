package com.shayor.apkextractor.ui

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
import com.shayor.apkextractor.data.ApkExporter
import com.shayor.apkextractor.data.AppFilter
import com.shayor.apkextractor.data.PackageType
import com.shayor.apkextractor.data.AppIcons
import com.shayor.apkextractor.data.AppInfo
import com.shayor.apkextractor.data.AppListCache
import com.shayor.apkextractor.data.AppRepository
import com.shayor.apkextractor.data.AutoBackup
import com.shayor.apkextractor.data.ExportException
import com.shayor.apkextractor.data.ExportStage
import com.shayor.apkextractor.data.ExportedFile
import com.shayor.apkextractor.data.HistoryEntry
import com.shayor.apkextractor.data.HistoryRepository
import com.shayor.apkextractor.data.NameFormat
import com.shayor.apkextractor.data.Settings
import com.shayor.apkextractor.data.SettingsRepository
import com.shayor.apkextractor.data.SortBy
import com.shayor.apkextractor.data.SplitMode
import com.shayor.apkextractor.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
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
    /** The (trimmed) search the list is filtered by, for highlighting matches. */
    val query: String = "",
)

/** The filtered + sorted list before the search is applied; only rebuilt when apps or sorting change. */
private class SortedApps(val all: List<AppInfo>, val visible: List<AppInfo>)

enum class TaskKind { EXTRACT, SHARE }

/** Long-press launcher shortcuts (res/xml/shortcuts.xml). */
enum class Shortcut(val action: String) {
    INSTALL("com.shayor.apkextractor.action.INSTALL"),
    BACKUP_ALL("com.shayor.apkextractor.action.BACKUP_ALL"),
    ;

    companion object {
        fun of(action: String?) = entries.firstOrNull { it.action == action }
    }
}

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
        val stage: ExportStage? = null,
        /** [SystemClock.elapsedRealtime] when the whole task started; drives the time-left estimate. */
        val startedAt: Long = 0L,
        /** All work is done; briefly shows a "ready" state before the share sheet / result appears. */
        val finished: Boolean = false,
        val savedCount: Int = 0,
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

    /** A message with an "Undo" action that runs [undo] if tapped before the snackbar goes away. */
    class Undoable(val text: String, val undo: () -> Unit) : UiEvent
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
    private val cache = AppListCache(app)
    private val exporter = ApkExporter(app)
    private val historyRepo = HistoryRepository.get(app)
    private val autoBackup = AutoBackup.get(app)

    /** Apps that are saved automatically whenever they update. */
    val autoBackupApps: StateFlow<Set<String>> = autoBackup.watched
    /** Each watched split app's own format (APK or APKS); missing ones follow Settings. */
    val autoBackupFormats: StateFlow<Map<String, SplitMode>> = autoBackup.formats

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

    /** Filter + sort, which is the costly part (collation); typing a search doesn't redo it. */
    private val sortedApps = combine(
        allApps,
        settings.map { ListOptions(it.filter, it.packageType, it.sortBy, it.sortAscending) }.distinctUntilChanged(),
    ) { apps, (filter, type, sortBy, ascending) ->
        apps?.let {
            val visible = it.filter { app ->
                val owner = when (filter) {
                    AppFilter.USER -> !app.isSystem
                    AppFilter.SYSTEM -> app.isSystem
                    AppFilter.ALL -> true
                }
                owner && when (type) {
                    PackageType.ALL -> true
                    PackageType.SPLIT -> app.isSplit
                    PackageType.SINGLE -> !app.isSplit
                }
            }.sortedWith(comparator(sortBy, ascending))
            SortedApps(it, visible)
        }
    }.flowOn(Dispatchers.Default)

    /** Typing is debounced so a fast typist doesn't filter on every keystroke; clearing is instant. */
    @OptIn(FlowPreview::class)
    private val searchQuery = query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()

    val listState: StateFlow<ListState> = combine(sortedApps, loadError, searchQuery) { sorted, error, needle ->
        if (sorted == null) return@combine ListState(loading = error == null, error = error)
        val visible = if (needle.isEmpty()) {
            sorted.visible
        } else {
            sorted.visible.filter {
                it.label.contains(needle, ignoreCase = true) || it.packageName.contains(needle, ignoreCase = true)
            }
        }
        ListState(
            loading = false,
            error = error,
            apps = visible,
            totalBytes = visible.sumOf { it.sizeBytes },
            userCount = sorted.all.count { !it.isSystem },
            systemCount = sorted.all.count { it.isSystem },
            query = needle,
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
            if (intent.action != Intent.ACTION_PACKAGE_REMOVED) autoBackup.onPackageChanged(pkg)
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

        load(fromCache = true)
        prepareFirstScreen()
        verifyOutputFolder()
        viewModelScope.launch { exporter.trimShareCache() }
        autoBackup.schedule()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
    }

    // ---------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------

    /**
     * [fromCache]: show the list saved by the previous launch straight away, then swap in the
     * fresh one. Reading every app's label is the slow part of startup; the cache skips the wait.
     */
    private fun load(fromCache: Boolean = false) = viewModelScope.launch {
        if (fromCache && allApps.value == null) {
            cache.read()?.let { cached -> if (allApps.value == null) allApps.value = cached }
        }
        try {
            val apps = repo.loadAll()
            allApps.value = apps
            loadError.value = null
            val installed = apps.mapTo(HashSet()) { it.packageName }
            _selection.update { it.filterTo(HashSet()) { p -> p in installed } }
            cache.write(apps)
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
        allApps.value?.let { cache.write(it) }
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

    fun setPackageType(type: PackageType) = settingsRepo.update { it.copy(packageType = type) }

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
    // Launcher shortcuts
    // ---------------------------------------------------------------------------------------------

    private val _shortcut = MutableStateFlow<Shortcut?>(null)

    /** A launcher shortcut the UI still has to act on (switch tab, open the file picker). */
    val shortcut: StateFlow<Shortcut?> = _shortcut.asStateFlow()

    fun onShortcut(shortcut: Shortcut) {
        _shortcut.value = shortcut
        if (shortcut == Shortcut.BACKUP_ALL) selectAllUserApps()
    }

    fun consumeShortcut() {
        _shortcut.value = null
    }

    /** "Back up all apps" shortcut: selects every installed (non-system) app, ready to extract. */
    private fun selectAllUserApps() = viewModelScope.launch {
        settingsRepo.update { it.copy(filter = AppFilter.USER, packageType = PackageType.ALL) }
        val apps = allApps.first { it != null }.orEmpty()
        _selection.value = apps.filterNot { it.isSystem }.mapTo(HashSet()) { it.packageName }
    }

    // ---------------------------------------------------------------------------------------------
    // Auto-backup
    // ---------------------------------------------------------------------------------------------

    /** [format]: for a split app, how its backups are saved (its Save As choice); null = Settings default. */
    fun setAutoBackup(pkg: String, enabled: Boolean, format: SplitMode? = null) {
        autoBackup.set(pkg, enabled, format)
        _events.trySend(
            UiEvent.Message(
                when {
                    !enabled -> "Auto-backup turned off"
                    // Watched but not installed (e.g. from the Auto-Backup list): nothing to save yet.
                    allApps.value?.none { it.packageName == pkg } == true -> "It's saved again once the app is reinstalled"
                    else -> "Saving this version now, and every update from here on"
                },
            ),
        )
    }

    /** A watched split app's Save As choice changed: its next backups use it. */
    fun setAutoBackupFormat(pkg: String, format: SplitMode) = autoBackup.setFormat(pkg, format)

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

        val started = SystemClock.elapsedRealtime()
        taskJob = viewModelScope.launch {
            val saved = mutableListOf<ExportedFile>()
            val savedApps = mutableListOf<SavedApp>()
            val failures = mutableListOf<Pair<String, String>>()
            try {
                val shareDir = if (kind == TaskKind.SHARE) exporter.newShareDir() else null
                for ((index, app) in apps.withIndex()) {
                    _task.value = TaskState.Running(
                        kind, app.label, app.packageName, app.iconKey, index, apps.size, done.get(), estimate,
                        startedAt = started,
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
                    val onStage: (ExportStage?) -> Unit = { stage ->
                        _task.update { s -> if (s is TaskState.Running) s.copy(stage = stage) else s }
                    }
                    try {
                        val file = if (shareDir != null) {
                            exporter.exportForShare(app, settings, shareDir, onStage, onProgress)
                        } else {
                            // History swaps out an identical earlier copy and may rename the file.
                            exporter.exportToStorage(app, settings, onStage, onProgress).let {
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
                if (saved.isNotEmpty()) showFinished(saved.size, started)
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

    /**
     * Small apps copy in a few milliseconds, so the progress dialog used to flash and the share sheet
     * popped up as if by accident. Fill the ring, show a check for a moment, and keep the whole dialog
     * up long enough to be read, so the result clearly follows from the tap.
     */
    private suspend fun showFinished(savedCount: Int, started: Long) {
        _task.update { s ->
            if (s is TaskState.Running) {
                val total = s.bytesTotal.coerceAtLeast(1)
                s.copy(bytesDone = total, bytesTotal = total, stage = null, finished = true, savedCount = savedCount)
            } else {
                s
            }
        }
        val elapsed = SystemClock.elapsedRealtime() - started
        delay(maxOf(DONE_HOLD_MS, MIN_DIALOG_MS - elapsed))
    }

    private fun report(
        kind: TaskKind,
        saved: List<ExportedFile>,
        savedApps: List<SavedApp>,
        failures: List<Pair<String, String>>,
        settings: Settings,
    ) {
        if (kind == TaskKind.SHARE) {
            if (saved.isNotEmpty()) {
                _events.trySend(UiEvent.Launch(exporter.shareIntent(saved)))
                // Done with these apps, as after extracting. If nothing could be shared, the
                // selection stays so the user can try again.
                clearSelection()
            }
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

    /** Removes an entry (the file itself stays on disk); it can be put back from the snackbar. */
    fun removeHistory(entry: HistoryEntry) {
        historyRepo.remove(entry.id)
        _events.trySend(UiEvent.Undoable("Removed ${entry.label} from history") { historyRepo.restore(listOf(entry)) })
    }

    fun clearHistory() {
        val removed = history.value
        if (removed.isEmpty()) return
        historyRepo.clear()
        _events.trySend(UiEvent.Undoable("History cleared") { historyRepo.restore(removed) })
    }

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
        const val SEARCH_DEBOUNCE_MS = 120L
        const val DONE_HOLD_MS = 450L
        const val MIN_DIALOG_MS = 900L
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

/** What the app list is filtered and sorted by; a change re-filters and re-sorts the list. */
private data class ListOptions(
    val filter: AppFilter,
    val type: PackageType,
    val sortBy: com.shayor.apkextractor.data.SortBy,
    val ascending: Boolean,
)
