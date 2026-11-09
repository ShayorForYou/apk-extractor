package com.extractor.data

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
) {
    val file: ExportedFile get() = ExportedFile(Uri.parse(uri), fileName, mimeType, location)
}

/**
 * Remembers the last [MAX] extractions so they can be found again from the History tab.
 * Stored as a small JSON array in SharedPreferences.
 */
class HistoryRepository private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    suspend fun add(app: AppInfo, file: ExportedFile, treeUri: String?) {
        val size = withContext(Dispatchers.IO) { querySize(file.uri) }
        val entry = HistoryEntry(
            id = System.nanoTime(),
            label = app.label,
            packageName = app.packageName,
            versionName = app.displayVersion,
            fileName = file.displayName,
            uri = file.uri.toString(),
            mimeType = file.mimeType,
            location = file.location,
            treeUri = treeUri,
            sizeBytes = size,
            time = System.currentTimeMillis(),
        )
        _entries.update { (listOf(entry) + it).take(MAX) }
        write()
    }

    fun remove(id: Long) {
        _entries.update { list -> list.filterNot { it.id == id } }
        write()
    }

    fun clear() {
        _entries.value = emptyList()
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
                    .put("time", e.time),
            )
        }
        prefs.edit { putString(KEY, array.toString()) }
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX = 100

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
