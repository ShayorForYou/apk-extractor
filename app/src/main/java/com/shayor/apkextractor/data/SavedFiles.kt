package com.shayor.apkextractor.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * File operations on files the app saved, wherever they are: a folder the user picked (a document
 * tree), Download/APK Extractor through MediaStore (Android 10+), or plain files (Android 9 and older).
 * A save folder is identified by its tree URI, or null for the default folder.
 */
class SavedFiles(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val authority = "${this.context.packageName}.fileprovider"

    /** Deletes a saved file; false if it is already gone or no longer ours to delete. */
    fun delete(uri: Uri): Boolean = runCatching {
        when {
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.deleteDocument(resolver, uri)
            uri.authority == authority -> legacyFile(uri)?.let { f -> f.delete().also { if (it) scan(f) } } ?: false
            else -> resolver.delete(uri, null, null) > 0
        }
    }.onFailure { Log.w(TAG, "Couldn't delete $uri", it) }.getOrDefault(false)

    /** Gives [file] the name [name]; null where it can't be renamed (or [name] is taken). */
    fun rename(file: ExportedFile, name: String): ExportedFile? = runCatching {
        when {
            DocumentsContract.isDocumentUri(context, file.uri) ->
                DocumentsContract.renameDocument(resolver, file.uri, name)?.let { file.copy(uri = it, displayName = name) }

            file.uri.authority == MediaStore.AUTHORITY -> {
                fun setName(n: String) = resolver.update(
                    file.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, n) }, null, null,
                ) > 0
                if (!setName(name)) return@runCatching null
                // If the name is taken by a file we can't see (another app's, or ours from before a
                // reinstall), MediaStore quietly picks "name (2)" instead. Then keep the original.
                if (displayName(file.uri) == name) {
                    file.copy(displayName = name)
                } else {
                    setName(file.displayName)
                    null
                }
            }

            file.uri.authority == authority -> {
                val from = legacyFile(file.uri) ?: return@runCatching null
                val to = File(from.parentFile, name)
                if (to.exists() || !from.renameTo(to)) return@runCatching null
                scan(from)
                scan(to)
                file.copy(uri = FileProvider.getUriForFile(context, authority, to), displayName = name)
            }

            else -> null
        }
    }.onFailure { Log.w(TAG, "Couldn't rename ${file.uri}", it) }.getOrNull()

    /** The file named exactly [name] in the save folder [tree] (null = default folder), if there is one. */
    fun findByName(tree: String?, name: String): Uri? = runCatching {
        when {
            tree != null -> {
                val treeUri = Uri.parse(tree)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri),
                )
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                // Document providers ignore selections, so the folder is scanned (names only: cheap).
                resolver.query(children, projection, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1) == name && c.getString(2) != DocumentsContract.Document.MIME_TYPE_DIR) {
                            return@runCatching DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(0))
                        }
                    }
                }
                null
            }

            Build.VERSION.SDK_INT >= 29 -> {
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf("${Environment.DIRECTORY_DOWNLOADS}/${ApkExporter.FOLDER}/", name),
                    null,
                )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
            }

            else -> File(legacyDir(), name).takeIf { it.isFile }?.let { FileProvider.getUriForFile(context, authority, it) }
        }
    }.onFailure { Log.w(TAG, "Couldn't look for $name", it) }.getOrNull()

    /** The file's size (-1 if the provider doesn't know it), or null when it can't be read at all. */
    fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) (if (c.isNull(0)) -1L else c.getLong(0)) else null
        }
    }.getOrNull()

    /**
     * Whether the file behind [uri] can be opened right now. Opening a descriptor checks the file itself,
     * not just a listing row, which can outlive a file deleted by hand or by another app.
     */
    fun isAvailable(uri: Uri): Boolean = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)

    /**
     * Whether the save folder [tree] (null = default folder) can be reached right now: its access is
     * still granted and its root answers. An unmounted card or a revoked permission is not a missing file.
     */
    fun folderReachable(tree: String?): Boolean {
        if (tree == null) return true
        return runCatching {
            val treeUri = Uri.parse(tree)
            val granted = resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
            val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
            granted && resolver.query(root, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }

    /**
     * Moves the saved file of [entry] into the save folder [toTree] (null = default folder), named
     * [toLabel] in the UI. The original is removed once the copy is complete; if anything fails, the
     * original stays where it was and nothing is left behind in the new folder.
     *
     * Call it on a background thread. Once the file is in its new place this returns normally even if
     * the caller was cancelled meanwhile, so the caller can always record where the file went.
     */
    suspend fun move(
        entry: HistoryEntry,
        toTree: String?,
        toLabel: String,
        exporter: ApkExporter,
        onProgress: (Long) -> Unit,
    ): ExportedFile {
        val source = Uri.parse(entry.uri)
        val size = sizeOf(source) ?: throw ExportException(ApkExporter.FILE_MISSING)
        // "App_v1 (1).apk" only had its suffix because the plain name was taken where it was saved.
        val plain = withoutCopySuffix(entry.fileName)
        val name = if (plain != entry.fileName && findByName(toTree, plain) == null) plain else entry.fileName

        // The same name is already there with the same size: an earlier copy of this very file.
        findByName(toTree, entry.fileName)?.let { existing ->
            if (!sameFile(existing, source) && size >= 0 && sizeOf(existing) == size) {
                delete(source)
                onProgress(size)
                return ExportedFile(existing, entry.fileName, entry.mimeType, toLabel)
            }
        }

        // Both folders on the same storage: the provider can move it in place, which is instant.
        if (entry.treeUri != null && toTree != null) {
            moveDocument(source, entry.treeUri, toTree)?.let { moved ->
                onProgress(size.coerceAtLeast(0))
                val file = ExportedFile(moved, displayName(moved) ?: entry.fileName, entry.mimeType, toLabel)
                return if (name != file.displayName) rename(file, name) ?: file else file
            }
        }

        val copy = exporter.copyToFolder(source, name, entry.mimeType, size, toTree, toLabel, onProgress)
        if (!delete(source)) Log.w(TAG, "Copied ${entry.fileName}, but couldn't remove the original")
        return copy
    }

    private fun moveDocument(source: Uri, fromTree: String, toTree: String): Uri? {
        val from = Uri.parse(fromTree)
        val to = Uri.parse(toTree)
        if (source.authority != to.authority || from.authority != to.authority) return null
        return try {
            val fromDir = DocumentsContract.buildDocumentUriUsingTree(from, DocumentsContract.getTreeDocumentId(from))
            val toDir = DocumentsContract.buildDocumentUriUsingTree(to, DocumentsContract.getTreeDocumentId(to))
            // The result is expressed under the source's tree, whose access may be released later.
            DocumentsContract.moveDocument(resolver, source, fromDir, toDir)?.let { moved ->
                DocumentsContract.buildDocumentUriUsingTree(to, DocumentsContract.getDocumentId(moved))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not supported, a different volume, or the name is taken: copying handles all of these.
            Log.i(TAG, "In-place move not possible, copying instead: ${e.message}")
            null
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** The real file behind one of our FileProvider URIs (Android 9 and older saves). */
    private fun legacyFile(uri: Uri): File? {
        // content://<authority>/downloads/APK Extractor/<name>; see res/xml/file_paths.xml.
        val segments = uri.pathSegments
        if (segments.size < 2 || segments[0] != "downloads") return null
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloads, segments.drop(1).joinToString("/"))
        // Never step outside Download (e.g. through "..").
        return file.takeIf { it.canonicalPath.startsWith(downloads.canonicalPath + File.separator) }
    }

    @Suppress("DEPRECATION")
    private fun legacyDir() = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        ApkExporter.FOLDER,
    )

    private fun scan(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.path), null, null)
    }

    companion object {
        private const val TAG = "SavedFiles"

        /**
         * Whether two URIs point at the same file. The same file can be reached through differently
         * spelled URIs: a document through any tree that contains it, a MediaStore row through the
         * "external" or "external_primary" volume.
         */
        fun sameFile(a: Uri, b: Uri): Boolean {
            if (a == b) return true
            if (a.authority != b.authority) return false
            if (a.authority == MediaStore.AUTHORITY) {
                return a.lastPathSegment != null && a.lastPathSegment == b.lastPathSegment &&
                    a.pathSegments.getOrNull(1) == b.pathSegments.getOrNull(1) // same table (downloads)
            }
            val docA = documentId(a)
            return docA != null && docA == documentId(b)
        }

        fun sameFile(a: String, b: Uri) = sameFile(Uri.parse(a), b)

        private val COPY_SUFFIX = Regex("""^(.+?) \(\d{1,3}\)(\.[^.]+)?$""")

        /** "App_v1 (2).apk" → "App_v1.apk"; other names are returned as they are. */
        fun withoutCopySuffix(name: String): String =
            COPY_SUFFIX.matchEntire(name)?.let { it.groupValues[1] + it.groupValues[2] } ?: name

        /** The document id of a document URI ("…/document/<id>"), without needing a Context. */
        private fun documentId(uri: Uri): String? {
            val s = uri.pathSegments
            val i = s.indexOf("document")
            return if (i >= 0 && i + 1 < s.size) s[i + 1] else null
        }
    }
}
