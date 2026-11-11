package com.shayor.apkextractor.data

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

/**
 * Remembers the last [MAX] extractions so they can be found again from the History tab.
 * Stored as a small JSON array in SharedPreferences.
 */
class HistoryRepository private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    /**
     * Records a newly saved [file]. A file saved earlier for the same app version and type is an
     * identical copy, so it is deleted and dropped from the list, and the new file takes over its
     * name (no "App (1).apk" pile-up). Returns the file as finally stored (it may have been renamed).
     */
    suspend fun add(app: AppInfo, file: ExportedFile, treeUri: String?, auto: Boolean = false): ExportedFile =
        withContext(Dispatchers.IO) {
            val extension = file.displayName.substringAfterLast('.', "")
            val duplicates = _entries.value.filter { it.isSameVersion(app, extension) && it.uri != file.uri.toString() }
            var stored = file
            // Take over the plainest freed name ("App_v1.apk" rather than "App_v1 (1).apk").
            val freedName = duplicates.filter { deleteFile(Uri.parse(it.uri)) }.minByOrNull { it.fileName.length }?.fileName
            if (freedName != null && freedName != file.displayName) rename(file, freedName)?.let { stored = it }

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
            val replaced = duplicates.mapTo(HashSet()) { it.id }
            _entries.update { list -> (listOf(entry) + list.filterNot { it.id in replaced }).take(MAX) }
            write()
            stored
        }

    /** The saved file of exactly this app version and type, if there is one in the list. */
    fun findSaved(app: AppInfo, extension: String): HistoryEntry? =
        _entries.value.firstOrNull { it.isSameVersion(app, extension) }

    /** Deletes a file this app saved; false if it is already gone or no longer ours to delete. */
    private fun deleteFile(uri: Uri): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    }.onFailure { Log.w(TAG, "Couldn't delete duplicate $uri", it) }.getOrDefault(false)

    /** Gives [file] the name [name]; null where the storage can't rename (then the new name is kept). */
    private fun rename(file: ExportedFile, name: String): ExportedFile? = runCatching {
        val resolver = context.contentResolver
        when {
            DocumentsContract.isDocumentUri(context, file.uri) ->
                DocumentsContract.renameDocument(resolver, file.uri, name)?.let { file.copy(uri = it, displayName = name) }

            file.uri.authority == MediaStore.AUTHORITY -> {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
                if (resolver.update(file.uri, values, null, null) > 0) file.copy(displayName = name) else null
            }

            else -> null // FileProvider files (Android 9 and older) keep the name they were written with
        }
    }.getOrNull()

    fun remove(id: Long) {
        _entries.update { list -> list.filterNot { it.id == id } }
        write()
    }

    fun clear() {
        _entries.value = emptyList()
        write()
    }

    /** Puts back entries that were just removed (undo), in their original order by time. */
    fun restore(restored: List<HistoryEntry>) {
        if (restored.isEmpty()) return
        // Fresh ids: the list remembers per-id row state (e.g. "swiped away"), which must not come back.
        val base = System.nanoTime()
        val revived = restored.mapIndexed { i, e -> e.copy(id = base + i) }
        _entries.update { list ->
            val present = list.mapTo(HashSet()) { it.uri }
            (list + revived.filterNot { it.uri in present }).sortedByDescending { it.time }.take(MAX)
        }
        write()
    }

    /** False once the user has deleted or moved the file. */
    suspend fun exists(entry: HistoryEntry): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(Uri.parse(entry.uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    private fun querySize(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun read(): List<HistoryEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            HistoryEntry(
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
        }
    }.getOrDefault(emptyList())

    private fun write() {
        val array = JSONArray()
        _entries.value.forEach { e ->
            array.put(
                JSONObject()
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
                    .put("code", e.versionCode),
            )
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    companion object {
        private const val TAG = "History"
        private const val KEY = "entries"
        // Room for several versions of each app (auto-backup keeps adding new ones).
        private const val MAX = 200

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
