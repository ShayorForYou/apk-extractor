package com.shayor.apkextractor.data

import android.Manifest
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * Copies an app's APK (or a split-APK bundle) to user storage.
 *
 * Every export is transactional: if anything fails or the user cancels, the partially
 * written file is removed so no corrupt APKs are ever left behind.
 */
class ApkExporter(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val pm = this.context.packageManager
    private val authority = "${this.context.packageName}.fileprovider"
    private val shareRoot get() = File(context.cacheDir, "share")

    // ---------------------------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------------------------

    /** Saves [app] to the user's configured location. */
    suspend fun exportToStorage(
        app: AppInfo,
        settings: Settings,
        onStage: (String?) -> Unit,
        onProgress: (Long) -> Unit,
    ): ExportedFile = withPreparedSources(app, settings, onStage) { sources ->
        val name = FileNames.build(app, settings.nameFormat, sources.extension)
        val tree = settings.outputTreeUri
        val destination = when {
            tree != null -> TreeDestination(
                Uri.parse(tree), name, sources.mimeType, settings.outputFolderName ?: "selected folder",
            )

            Build.VERSION.SDK_INT >= 29 -> {
                ensureSpace(Environment.getExternalStorageDirectory(), sources.totalBytes)
                MediaStoreDestination(name, sources.mimeType)
            }

            else -> {
                val dir = legacyDownloadsDir()
                ensureSpace(dir, sources.totalBytes)
                FileDestination(uniqueFile(dir, name), sources.mimeType, DEFAULT_LOCATION, scan = true)
            }
        }
        write(sources, destination, onProgress)
    }

    /** Creates a fresh, empty directory for files that are about to be shared. */
    suspend fun newShareDir(): File = withContext(Dispatchers.IO) {
        shareRoot.deleteRecursively()
        File(shareRoot, System.currentTimeMillis().toString()).apply { mkdirs() }
    }

    /** Writes [app] into [dir] (see [newShareDir]) and returns a FileProvider-backed file. */
    suspend fun exportForShare(
        app: AppInfo,
        settings: Settings,
        dir: File,
        onStage: (String?) -> Unit,
        onProgress: (Long) -> Unit,
    ): ExportedFile = withPreparedSources(app, settings, onStage) { sources ->
        ensureSpace(dir, sources.totalBytes)
        val name = FileNames.build(app, settings.nameFormat, sources.extension)
        write(sources, FileDestination(uniqueFile(dir, name), sources.mimeType, "cache", scan = false), onProgress)
    }

    fun shareIntent(files: List<ExportedFile>): Intent {
        require(files.isNotEmpty())
        val mime = files.map { it.mimeType }.distinct().singleOrNull() ?: MIME_BINARY
        val send = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, files[0].uri)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { it.uri }))
        }
        send.type = mime
        // ClipData is what actually carries the read grant to the receiving app (and through the chooser).
        send.clipData = ClipData.newRawUri(files[0].displayName, files[0].uri).apply {
            files.drop(1).forEach { addItem(ClipData.Item(it.uri)) }
        }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null)
    }

    fun hasTreeAccess(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    fun folderName(tree: Uri): String? = runCatching {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        queryDisplayName(doc)
    }.getOrNull()

    suspend fun shareCacheSize(): Long = withContext(Dispatchers.IO) {
        shareRoot.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun clearShareCache() = withContext(Dispatchers.IO) { shareRoot.deleteRecursively() }

    /** Removes share files and interrupted merges left over from earlier sessions. */
    suspend fun trimShareCache(maxAgeMs: Long = 60 * 60 * 1000L) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        shareRoot.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        mergeRoot.deleteRecursively()
    }

    // ---------------------------------------------------------------------------------------------
    // Split APK merging
    // ---------------------------------------------------------------------------------------------

    private val mergeRoot get() = File(context.cacheDir, "merge")
    private val merger = ApkMerger { DeviceSigningKey.get() }

    /**
     * Resolves the app's current files and, for split apps in [SplitMode.MERGE], first merges
     * them into one signed APK in the cache. [block] then writes whatever needs writing; the
     * temporary merge output is always cleaned up afterwards.
     */
    private suspend fun <T> withPreparedSources(
        app: AppInfo,
        settings: Settings,
        onStage: (String?) -> Unit,
        block: suspend (Sources) -> T,
    ): T = withContext(Dispatchers.IO) {
        val raw = resolveSources(app.packageName, includeSplits = settings.splitMode != SplitMode.BASE_ONLY)
        if (!raw.isBundle || settings.splitMode != SplitMode.MERGE) return@withContext block(raw)

        // The merge needs room for an unsigned + a signed copy.
        ensureSpace(context.cacheDir, raw.totalBytes * 2)
        val work = File(mergeRoot, System.nanoTime().toString())
        try {
            onStage("Merging…")
            val merged = try {
                runInterruptible { merger.merge(raw.all, work) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                throw ExportException(MERGE_FAILED, e)
            } catch (e: Exception) {
                throw ExportException(MERGE_FAILED, e)
            }
            onStage(null)
            block(Sources(merged, emptyList()))
        } finally {
            withContext(NonCancellable) { work.deleteRecursively() }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sources
    // ---------------------------------------------------------------------------------------------

    private class Sources(val base: File, val splits: List<File>) {
        val all = listOf(base) + splits
        val isBundle get() = splits.isNotEmpty()
        val totalBytes = all.sumOf { it.length() }
        val extension get() = if (isBundle) "apks" else "apk"
        val mimeType get() = if (isBundle) MIME_BINARY else MIME_APK
    }

    /** Paths change whenever an app updates, so they are always re-read right before copying. */
    private fun resolveSources(packageName: String, includeSplits: Boolean): Sources {
        val ai = try {
            pm.applicationInfoCompat(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            throw ExportException("This app is no longer installed")
        }
        val base = ai.sourceDir?.let(::File) ?: throw ExportException(CANT_READ)
        val splits = if (includeSplits) ai.splitSourceDirs?.filterNotNull()?.map(::File).orEmpty() else emptyList()
        for (f in listOf(base) + splits) {
            if (!f.isFile || !f.canRead()) {
                throw ExportException(CANT_READ)
            }
        }
        return Sources(base, splits)
    }

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    private suspend fun write(sources: Sources, dest: Destination, onProgress: (Long) -> Unit): ExportedFile {
        var committed = false
        try {
            dest.create()
            dest.openStream().use { out ->
                if (sources.isBundle) writeBundle(sources, out, onProgress)
                else copyFile(sources.base, out, dest.supportsChannels, onProgress)
            }
            return dest.commit().also { committed = true }
        } catch (e: Throwable) {
            throw when (e) {
                is CancellationException, is ExportException -> e
                else -> {
                    Log.w(TAG, "Export to ${dest.javaClass.simpleName} failed", e)
                    ExportException(describe(e), e)
                }
            }
        } finally {
            if (!committed) withContext(NonCancellable) { runCatching { dest.discard() } }
        }
    }

    /** Writes an SAI-compatible .apks archive: base.apk + split_*.apk at the root. */
    private suspend fun writeBundle(sources: Sources, out: OutputStream, onProgress: (Long) -> Unit) {
        val zip = ZipOutputStream(BufferedOutputStream(out, BUFFER_SIZE))
        // Fastest deflate: APK payloads are mostly compressed already, but uncompressed
        // native libs / resources.arsc still shrink noticeably.
        zip.setLevel(Deflater.BEST_SPEED)
        val used = HashSet<String>()
        for (file in sources.all) {
            val preferred = if (file == sources.base) "base.apk" else file.name
            val entryName = uniqueEntryName(preferred, used)
            zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
            openSource(file).use { input -> copyStream(input, zip, onProgress) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    private suspend fun copyFile(file: File, out: OutputStream, allowChannels: Boolean, onProgress: (Long) -> Unit) {
        openSource(file).use { input ->
            if (allowChannels && out is FileOutputStream) {
                // Kernel-level copy (sendfile) — much faster than shuffling bytes through the JVM.
                val inCh = input.channel
                val outCh = out.channel
                val size = inCh.size()
                var pos = 0L
                while (pos < size) {
                    currentCoroutineContext().ensureActive()
                    val n = inCh.transferTo(pos, min(CHANNEL_CHUNK, size - pos), outCh)
                    if (n <= 0) break
                    pos += n
                    onProgress(n)
                }
                if (pos >= size) return
                inCh.position(pos) // fall through and finish with a plain stream copy
            }
            copyStream(input, out, onProgress)
        }
    }

    private suspend fun copyStream(input: InputStream, out: OutputStream, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        val ctx = currentCoroutineContext()
        while (true) {
            ctx.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            onProgress(read.toLong())
        }
    }

    private fun openSource(file: File): FileInputStream = try {
        FileInputStream(file)
    } catch (e: FileNotFoundException) {
        throw ExportException("The app was just updated. Try again.", e)
    }

    private fun describe(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("ENOSPC") || msg.contains("No space", ignoreCase = true) ->
                NO_SPACE

            e is SecurityException ->
                FOLDER_LOST

            e is FileNotFoundException ->
                "Save folder not found"

            e is IOException -> "Couldn't save the file"
            else -> "Something went wrong. Try again."
        }
    }

    private fun ensureSpace(dir: File, needed: Long) {
        val free = runCatching { StatFs(dir.path).availableBytes }.getOrNull() ?: return
        if (free < needed + SPACE_MARGIN) {
            throw ExportException(NO_SPACE)
        }
    }

    private fun legacyDownloadsDir(): File {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            throw ExportException("Storage isn't available right now")
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) throw ExportException("Storage permission is needed")

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw ExportException(CANT_SAVE)
        return dir
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (file.exists()) {
            file = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return file
    }

    private fun uniqueEntryName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        var i = 1
        while (true) {
            val candidate = "${name.removeSuffix(".apk")}_$i.apk"
            if (used.add(candidate)) return candidate
            i++
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------------------------------------------------------------------------------------------
    // Destinations
    // ---------------------------------------------------------------------------------------------

    private abstract class Destination {
        /** Whether the output is known to be a regular file, so kernel-level copying is safe. */
        open val supportsChannels = true
        abstract fun create()
        abstract fun openStream(): OutputStream
        abstract fun commit(): ExportedFile
        abstract fun discard()
    }

    @RequiresApi(29)
    private inner class MediaStoreDestination(val name: String, val mime: String) : Destination() {
        private var uri: Uri? = null

        override fun create() {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1) // hidden from other apps until complete
            }
            uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: throw ExportException(CANT_SAVE)
        }

        override fun openStream(): OutputStream =
            resolver.openOutputStream(requireNotNull(uri), "w")
                ?: throw ExportException(CANT_SAVE)

        override fun commit(): ExportedFile {
            val u = requireNotNull(uri)
            resolver.update(u, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            return ExportedFile(u, queryDisplayName(u) ?: name, mime, DEFAULT_LOCATION)
        }

        override fun discard() {
            uri?.let { resolver.delete(it, null, null) }
        }
    }

    private inner class TreeDestination(
        val tree: Uri,
        val name: String,
        val mime: String,
        val label: String,
    ) : Destination() {
        // Document providers may be cloud-backed pipes, so stick to plain streams.
        override val supportsChannels = false
        private var doc: Uri? = null

        override fun create() {
            if (!hasTreeAccess(tree)) throw ExportException(FOLDER_LOST)
            val parent = try {
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            } catch (e: IllegalArgumentException) {
                throw ExportException(FOLDER_LOST, e)
            }
            doc = try {
                DocumentsContract.createDocument(resolver, parent, mime, name)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw ExportException("Save folder not found", e)
            } ?: throw ExportException(CANT_SAVE)
        }

        override fun openStream(): OutputStream =
            resolver.openOutputStream(requireNotNull(doc), "w")
                ?: throw ExportException(CANT_SAVE)

        override fun commit(): ExportedFile {
            val d = requireNotNull(doc)
            return ExportedFile(d, queryDisplayName(d) ?: name, mime, label)
        }

        override fun discard() {
            doc?.let { DocumentsContract.deleteDocument(resolver, it) }
        }
    }

    private inner class FileDestination(
        val file: File,
        val mime: String,
        val label: String,
        val scan: Boolean,
    ) : Destination() {
        override fun create() {
            file.parentFile?.mkdirs()
            if (!file.createNewFile() && !file.exists()) throw ExportException(CANT_SAVE)
        }

        override fun openStream(): OutputStream = FileOutputStream(file)

        override fun commit(): ExportedFile {
            if (scan) MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
            return ExportedFile(FileProvider.getUriForFile(context, authority, file), file.name, mime, label)
        }

        override fun discard() {
            file.delete()
        }
    }

    companion object {
        const val FOLDER = "APK Extractor"
        const val DEFAULT_LOCATION = "Download/$FOLDER"
        const val MIME_APK = "application/vnd.android.package-archive"
        const val MIME_BINARY = "application/octet-stream"
        const val FOLDER_LOST = "Lost access to the save folder. Pick it again in Settings."
        const val MERGE_FAILED = "Couldn't merge this app. Try \".apks bundle\" in Settings."
        const val CANT_READ = "This app can't be extracted"
        const val CANT_SAVE = "Couldn't save the file"
        const val NO_SPACE = "Not enough storage space"

        private const val TAG = "ApkExporter"
        private const val BUFFER_SIZE = 256 * 1024
        private const val CHANNEL_CHUNK = 8L * 1024 * 1024
        private const val SPACE_MARGIN = 16L * 1024 * 1024
    }
}
