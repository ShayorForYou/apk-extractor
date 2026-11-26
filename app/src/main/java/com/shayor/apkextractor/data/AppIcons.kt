package com.shayor.apkextractor.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import java.util.zip.ZipInputStream

/**
 * Loads app icons off the main thread at the exact pixel size they are drawn at,
 * and keeps them in a memory-bounded LRU cache so scrolling stays smooth.
 *
 * Saved backups outlive their apps, so their icons are kept on disk too ([load] with a backup):
 * a small PNG is stored while the app is still installed, and for a backup whose app was removed
 * before that, the icon is read once from the backup file itself.
 */
object AppIcons {

    private val cache = object : LruCache<String, ImageBitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: ImageBitmap) =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    /** Reading an icon out of a backup unpacks an APK: one at a time, and once per app per run. */
    private val backupLock = Mutex()
    private val noIconInBackup = Collections.synchronizedSet(HashSet<String>())

    /** Stored icons are kept at this size and scaled down to whatever is drawn. */
    private const val STORED_PX = 192

    fun cached(key: String, sizePx: Int): ImageBitmap? = cache.get("$key#$sizePx")

    /**
     * @param backupUri the saved file this icon stands for, if any. Then the icon is also kept on
     *   disk, and taken from there (or from the file) once the app is no longer installed.
     */
    suspend fun load(
        context: Context,
        packageName: String,
        key: String,
        sizePx: Int,
        backupUri: String? = null,
    ): ImageBitmap? {
        val cacheKey = "$key#$sizePx"
        cache.get(cacheKey)?.let { return it }
        val app = context.applicationContext
        val bitmap = withContext(dispatcher) {
            val installed = runCatching { app.packageManager.getApplicationIcon(packageName) }.getOrNull()
            when {
                installed != null -> {
                    if (backupUri != null) store(app, packageName, installed)
                    installed.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                }
                backupUri != null -> stored(app, packageName)?.let { scaled(it, sizePx) }
                else -> null
            }
        } ?: backupUri?.let { fromBackup(app, packageName, it) }?.let { scaled(it, sizePx) }
            ?: return null
        bitmap.prepareToDraw() // upload to GPU ahead of time
        return bitmap.asImageBitmap().also { cache.put(cacheKey, it) }
    }

    private fun iconFile(context: Context, packageName: String) =
        File(File(context.filesDir, "icons"), "$packageName.png")

    private fun stored(context: Context, packageName: String): Bitmap? {
        val file = iconFile(context, packageName)
        return if (file.isFile) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
    }

    /** Keeps a small copy of an installed app's icon, once (a few KB per backed-up app). */
    private fun store(context: Context, packageName: String, icon: Drawable) {
        val file = iconFile(context, packageName)
        if (file.isFile) return
        runCatching { write(file, icon.toBitmap(STORED_PX, STORED_PX, Bitmap.Config.ARGB_8888)) }
    }

    private fun write(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(file)) tmp.delete()
    }

    private fun scaled(bitmap: Bitmap, sizePx: Int): Bitmap =
        if (bitmap.width == sizePx && bitmap.height == sizePx) bitmap else bitmap.scale(sizePx, sizePx)

    /**
     * The icon inside a backup: a plain APK is read as it is; from a bundle (APKS / XAPK / APKM)
     * only the base APK is unpacked. The result is stored, so this happens once per app.
     */
    private suspend fun fromBackup(context: Context, packageName: String, uri: String): Bitmap? {
        if (packageName in noIconInBackup) return null
        return backupLock.withLock {
            withContext(Dispatchers.IO) {
                stored(context, packageName)
                    ?: readBackupIcon(context, Uri.parse(uri))?.also { runCatching { write(iconFile(context, packageName), it) } }
                    ?: null.also { noIconInBackup += packageName }
            }
        }
    }

    private fun readBackupIcon(context: Context, uri: Uri): Bitmap? {
        val dir = File(context.cacheDir, "backup-icon").apply { deleteRecursively(); mkdirs() }
        return try {
            extractBaseApk(context, uri, dir)?.let { iconOfApk(context.packageManager, it) }
        } catch (_: Exception) {
            null
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The APK to read the icon from: the file itself, or the base APK inside a bundle. */
    private fun extractBaseApk(context: Context, uri: Uri, dir: File): File? {
        val resolver = context.contentResolver
        var isApk = false
        var best: File? = null
        resolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "AndroidManifest.xml") {
                        isApk = true
                        break
                    }
                    val name = entry.name.substringAfterLast('/')
                    if (entry.isDirectory || !name.endsWith(".apk", ignoreCase = true)) continue
                    if (name.startsWith("split_") || name.startsWith("config.")) continue
                    val out = File(dir, if (name == "base.apk") "base.apk" else "candidate.apk")
                    out.outputStream().use { zip.copyTo(it) }
                    best = out
                    if (name == "base.apk") break
                }
            }
        } ?: return null
        if (!isApk) return best
        // The backup is itself an APK: the package parser needs it as a file.
        val out = File(dir, "app.apk")
        resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
        return out
    }

    private fun iconOfApk(pm: PackageManager, apk: File): Bitmap? {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, 0)
        } ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = apk.path
        app.publicSourceDir = apk.path
        return app.loadIcon(pm).toBitmap(STORED_PX, STORED_PX, Bitmap.Config.ARGB_8888)
    }

    private fun cacheSizeKb(): Int {
        val maxKb = Runtime.getRuntime().maxMemory() / 1024
        return (maxKb / 8).coerceIn(4L * 1024, 48L * 1024).toInt()
    }
}
