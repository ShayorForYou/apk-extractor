package com.shayor.apkextractor.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One file the user extracted. [treeUri] is set when it was saved to a custom folder. */
@Immutable
data class HistoryEntry(
    val id: Long,
    val label: String,
    val packageName: String,
    val versionName: String,
    val fileName: String,
    val uri: String,
    val mimeType: String,
    val location: String,
    val treeUri: String?,
    val sizeBytes: Long,
    val time: Long,
    /** Saved by auto-backup rather than by the user. */
    val auto: Boolean = false,
    /** -1 for entries saved before version codes were recorded. */
    val versionCode: Long = -1,
) {
    val file: ExportedFile get() = ExportedFile(Uri.parse(uri), fileName, mimeType, location)

    /** Same app, same version, same kind of file (.apk / .apks): a copy of the same thing. */
    fun isSameVersion(app: AppInfo, extension: String): Boolean =
        packageName == app.packageName &&
            fileName.substringAfterLast('.', "").equals(extension, ignoreCase = true) &&
            if (versionCode >= 0) versionCode == app.versionCode else versionName == app.displayVersion
}

/** What is known about a saved file right now. */
enum class Availability {
    /** The file can be opened. */
    Available,

    /** Its folder is reachable, but the file is gone (deleted by hand, moved, or removed by another app). */
    Missing,

    /** Its folder can't be reached (card unmounted, access revoked): the file may still be there. */
    FolderUnreachable,
}

/**
 * Decides the [Availability] of a saved file. The folder is checked first, so a file in a folder that
 * can't be reached is never reported as missing; [fileExists] is only asked when the folder is reachable.
 */
fun classify(treeUri: String?, folderReachable: (String?) -> Boolean, fileExists: () -> Boolean): Availability = when {
    !folderReachable(treeUri) -> Availability.FolderUnreachable
    fileExists() -> Availability.Available
    else -> Availability.Missing
}

/**
 * Remembers the last [MAX] extractions so they can be found again from the History tab.
 * Stored as a small JSON array in SharedPreferences.
 */
class HistoryRepository private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val files = SavedFiles(context)
    private val _entries = MutableStateFlow(read(KEY))
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    private val _missing = MutableStateFlow<Set<Long>>(emptySet())

    /** Ids of listed entries whose file is gone, as of the last [refreshAvailability]. */
    val missing: StateFlow<Set<Long>> = _missing.asStateFlow()

    private var lastCheck = 0L

    /**
     * Entries removed from the list whose files are deleted once their undo window has passed
     * ([purge]). Kept on disk too, so files are still deleted if the app is closed meanwhile.
     */
    private val trash = MutableStateFlow(readTrash())

    /** Serializes changes that touch files, so a background auto-backup can't race the UI. */
    private val fileLock = Mutex()

    /**
     * Records a newly saved [file]. A file saved earlier for the same app version and type is an
     * identical copy, so it is deleted and dropped from the list. The new file then takes the name
     * it was meant to have (no "App (1).apk" pile-up), also when an older copy of it was still in
     * the folder without being in History. Returns the file as finally stored (it may be renamed).
     */
    suspend fun add(app: AppInfo, file: ExportedFile, treeUri: String?, auto: Boolean = false): ExportedFile =
        withContext(Dispatchers.IO) {
            fileLock.withLock {
                val extension = file.displayName.substringAfterLast('.', "")
                val duplicates = _entries.value.filter {
                    it.isSameVersion(app, extension) && !SavedFiles.sameFile(it.uri, file.uri)
                }
                duplicates.forEach { files.delete(Uri.parse(it.uri)) }
                val replaced = duplicates.mapTo(HashSet()) { it.id }

                var stored = file
                val wanted = file.requestedName
                if (stored.displayName != wanted) {
                    // The name is taken by a file History doesn't list (left behind when the list was
                    // cleared, or by an older version of this app): same name, so the same app
                    // version. It gives way, unless History lists it as one of its other files.
                    val taken = files.findByName(treeUri, wanted)
                    val free = taken == null ||
                        (!isListed(taken, except = replaced) && files.delete(taken).also { if (it) forgetTrashed(taken) })
                    if (free) files.rename(stored, wanted)?.let { stored = it }
                }

                val entry = HistoryEntry(
                    id = System.nanoTime(),
                    label = app.label,
                    packageName = app.packageName,
                    versionName = app.displayVersion,
                    fileName = stored.displayName,
                    uri = stored.uri.toString(),
                    mimeType = stored.mimeType,
                    location = stored.location,
                    treeUri = treeUri,
                    sizeBytes = querySize(stored.uri),
                    time = System.currentTimeMillis(),
                    auto = auto,
                    versionCode = app.versionCode,
                )
                _entries.update { list -> (listOf(entry) + list.filterNot { it.id in replaced }).take(MAX) }
                write()
                stored
            }
        }

    private fun isListed(uri: Uri, except: Set<Long>) =
        _entries.value.any { it.id !in except && SavedFiles.sameFile(it.uri, uri) }

    /** The saved file of exactly this app version and type, if there is one in the list. */
    fun findSaved(app: AppInfo, extension: String): HistoryEntry? =
        _entries.value.firstOrNull { it.isSameVersion(app, extension) }

    /** Drops an entry whose file is gone; nothing is deleted. */
    fun remove(id: Long) {
        _entries.update { list -> list.filterNot { it.id == id } }
        _missing.update { it - id }
        write()
    }

    /**
     * Takes [removed] off the list. With [deleteFiles], their files are deleted by [purge] once the
     * undo window has passed; until then [restore] brings everything back untouched.
     */
    fun discard(removed: List<HistoryEntry>, deleteFiles: Boolean) {
        if (removed.isEmpty()) return
        val ids = removed.mapTo(HashSet()) { it.id }
        _entries.update { list -> list.filterNot { it.id in ids } }
        _missing.update { it - ids }
        trash.update { it + removed.map { e -> Trashed(e, deleteFiles) } }
        write()
        writeTrash()
    }

    /** Puts back entries that were just discarded (undo), in their original order by time. */
    fun restore(restored: List<HistoryEntry>) {
        val ids = restored.mapTo(HashSet()) { it.id }
        // Only what is still waiting in the trash: once purged, its files are gone.
        val back = trash.value.filter { it.entry.id in ids }.map { it.entry }
        if (back.isEmpty()) return
        trash.update { list -> list.filterNot { it.entry.id in ids } }
        // Fresh ids: the list remembers per-id row state (e.g. "swiped away"), which must not come back.
        val base = System.nanoTime()
        val revived = back.mapIndexed { i, e -> e.copy(id = base + i) }
        _entries.update { list ->
            (list + revived.filterNot { e -> list.any { SavedFiles.sameFile(it.uri, Uri.parse(e.uri)) } })
                .sortedByDescending { it.time }
                .take(MAX)
        }
        write()
        writeTrash()
    }

    /**
     * Deletes the files of discarded entries ([ids], or everything in the trash when null). A file
     * that is back on the list meanwhile (saved again under the same name) is never deleted.
     */
    suspend fun purge(ids: Collection<Long>? = null) = withContext(Dispatchers.IO) {
        fileLock.withLock {
            val due = trash.value.filter { ids == null || it.entry.id in ids }
            if (due.isEmpty()) return@withLock
            for (t in due) {
                val uri = Uri.parse(t.entry.uri)
                if (t.deleteFile && !isListed(uri, except = emptySet())) files.delete(uri)
            }
            val done = due.mapTo(HashSet()) { it.entry.id }
            trash.update { list -> list.filterNot { it.entry.id in done } }
            writeTrash()
        }
    }

    /** A file in the trash was deleted (replaced by a new save): it can no longer be restored. */
    private fun forgetTrashed(uri: Uri) {
        val before = trash.value
        trash.update { list -> list.filterNot { SavedFiles.sameFile(it.entry.uri, uri) } }
        if (trash.value.size != before.size) writeTrash()
    }

    /** Folders that discarded entries still need access to until they are purged. */
    fun trashedFolders(): Set<String> = trash.value.mapNotNullTo(HashSet()) { it.entry.treeUri }

    /** The file of entry [id] now lives at [file] in the save folder [treeUri] (it was moved). */
    suspend fun relocate(id: Long, file: ExportedFile, treeUri: String?) = fileLock.withLock {
        _entries.update { list ->
            // Moved onto a file that is already listed (the same file, saved twice): keep one entry.
            val clash = list.any { it.id != id && SavedFiles.sameFile(it.uri, file.uri) }
            if (clash) {
                list.filterNot { it.id == id }
            } else {
                list.map {
                    if (it.id != id) it
                    else it.copy(
                        uri = file.uri.toString(),
                        fileName = file.displayName,
                        location = file.location,
                        treeUri = treeUri,
                    )
                }
            }
        }
        write()
    }

    /** The [Availability] of one entry. Does file I/O: call it off the main thread. */
    fun availability(entry: HistoryEntry, folderReachable: (String?) -> Boolean = files::folderReachable): Availability =
        classify(entry.treeUri, folderReachable) { files.isAvailable(Uri.parse(entry.uri)) }

    /** Checks one entry right now, before it is shared or installed. */
    suspend fun checkNow(entry: HistoryEntry): Availability = withContext(Dispatchers.IO) { availability(entry) }

    /** The listed entries whose files were missing at the last [refreshAvailability]. */
    fun missingEntries(): List<HistoryEntry> = _entries.value.filter { it.id in _missing.value }

    /**
     * Re-checks every listed file in the background and records the missing ones. It is skipped while a
     * file operation holds the lock (its files are in motion), and, unless [force], when the last check
     * was recent, so resuming the app over and over stays cheap.
     */
    suspend fun refreshAvailability(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return@withContext
        if (!fileLock.tryLock()) return@withContext
        try {
            // Each folder is checked once per pass, not once per file.
            val reachable = HashMap<String?, Boolean>()
            _missing.value = _entries.value
                .filter { entry ->
                    availability(entry) { tree -> reachable.getOrPut(tree) { files.folderReachable(tree) } } ==
                        Availability.Missing
                }
                .mapTo(HashSet()) { it.id }
            lastCheck = System.currentTimeMillis()
        } finally {
            fileLock.unlock()
        }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private class Trashed(val entry: HistoryEntry, val deleteFile: Boolean)

    private fun read(key: String): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
    }.getOrDefault(emptyList())

    private fun readTrash(): List<Trashed> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TRASH, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Trashed(fromJson(o), o.optBoolean("delete"))
        }
    }.getOrDefault(emptyList())

    private fun write() {
        val array = JSONArray()
        _entries.value.forEach { array.put(toJson(it)) }
        prefs.edit { putString(KEY, array.toString()) }
    }

    private fun writeTrash() {
        val array = JSONArray()
        trash.value.forEach { array.put(toJson(it.entry).put("delete", it.deleteFile)) }
        prefs.edit { putString(KEY_TRASH, array.toString()) }
    }

    private fun fromJson(o: JSONObject) = HistoryEntry(
        id = o.getLong("id"),
        label = o.getString("label"),
        packageName = o.getString("pkg"),
        versionName = o.optString("version"),
        fileName = o.getString("file"),
        uri = o.getString("uri"),
        mimeType = o.getString("mime"),
        location = o.getString("location"),
        treeUri = o.optString("tree").ifBlank { null },
        sizeBytes = o.optLong("size"),
        time = o.getLong("time"),
        auto = o.optBoolean("auto"),
        versionCode = o.optLong("code", -1),
    )

    private fun toJson(e: HistoryEntry) = JSONObject()
        .put("id", e.id)
        .put("label", e.label)
        .put("pkg", e.packageName)
        .put("version", e.versionName)
        .put("file", e.fileName)
        .put("uri", e.uri)
        .put("mime", e.mimeType)
        .put("location", e.location)
        .put("tree", e.treeUri ?: "")
        .put("size", e.sizeBytes)
        .put("time", e.time)
        .put("auto", e.auto)
        .put("code", e.versionCode)

    companion object {
        private const val TAG = "History"
        private const val KEY = "entries"
        private const val KEY_TRASH = "trash"
        // Room for several versions of each app (auto-backup keeps adding new ones).
        private const val MAX = 200

        // How often resuming the app may re-check the files (an explicit check ignores this).
        private const val CHECK_INTERVAL_MS = 15_000L

        @Volatile
        private var instance: HistoryRepository? = null

        fun get(context: Context): HistoryRepository =
            instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }

        /**
         * Intents that open the folder holding an extracted file, best first. File managers differ,
         * so callers try each until one starts.
         */
        fun folderIntents(treeUri: String?): List<Intent> {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            val folder = if (treeUri != null) {
                runCatching {
                    val tree = Uri.parse(treeUri)
                    DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                }.getOrNull()
            } else {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download/${ApkExporter.FOLDER}",
                )
            }
            return listOfNotNull(
                folder?.let {
                    Intent(Intent.ACTION_VIEW).setDataAndType(it, DocumentsContract.Document.MIME_TYPE_DIR).addFlags(flags)
                },
                folder?.let { Intent(Intent.ACTION_VIEW).setDataAndType(it, "resource/folder").addFlags(flags) },
                Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
