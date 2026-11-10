package com.shayor.apkextractor.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.io.BufferedInputStream
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** An APK or split-APK bundle that has been read and is ready to install. */
@Immutable
data class PackageToInstall(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val icon: ImageBitmap?,
    /** base.apk first, then its splits (already filtered for this device's CPU). */
    val files: List<File>,
    val totalBytes: Long,
    /** Version of the same app already on the phone, or null if it isn't installed. */
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    /** False when the installed app was signed with a different key (install will fail). */
    val signatureMatches: Boolean,
    internal val certs: Set<String>,
    internal val baseFile: File,
) {
    val isBundle get() = files.size > 1
    val isInstalled get() = installedVersionCode != null
    val isDowngrade get() = installedVersionCode != null && versionCode < installedVersionCode
    val needsNewerAndroid get() = minSdk > Build.VERSION.SDK_INT
}

/** Result of a PackageInstaller session, delivered by [InstallResultReceiver]. */
sealed interface InstallResult {
    val sessionId: Int

    data class NeedsConfirmation(override val sessionId: Int, val intent: Intent) : InstallResult
    data class Success(override val sessionId: Int) : InstallResult
    data class Failure(override val sessionId: Int, val message: String, val cancelled: Boolean = false) : InstallResult
}

/**
 * Reads .apk / .apks / .xapk / .apkm files and installs them through a PackageInstaller session,
 * which handles single APKs and split bundles the same way.
 */
class ApkInstaller(private val context: Context) {

    private val pm = context.packageManager
    private val resolver = context.contentResolver
    private val workRoot = File(context.cacheDir, "install")

    fun canRequestInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || pm.canRequestPackageInstalls()

    /**
     * Reads the app inside [uri]. Bundles are streamed straight from the source in one pass, skipping
     * splits for other CPUs; only the base APK is parsed, and signatures are only checked when the
     * app is already installed (that check hashes the whole APK, so it is skipped when not needed).
     */
    suspend fun read(uri: Uri, iconPx: Int): PackageToInstall = withContext(Dispatchers.IO) {
        clear()
        val dir = File(workRoot, System.nanoTime().toString()).apply { mkdirs() }
        val size = querySize(uri)
        if (size > 0) ensureSpace(size)
        val apks = try {
            val name = displayName(uri).orEmpty().lowercase()
            if (name.endsWith(".apk")) {
                listOf(copy(uri, File(dir, "base.apk")))
            } else {
                // null = the zip is itself an APK (no recognisable extension).
                streamBundle(uri, dir) ?: listOf(copy(uri, File(dir, "base.apk")))
            }
        } catch (e: ExportException) {
            throw e
        } catch (e: SecurityException) {
            throw ExportException(CANT_OPEN, e)
        } catch (e: IOException) {
            throw ExportException(NOT_AN_APK, e)
        }
        if (apks.isEmpty()) throw ExportException(NOT_AN_APK)
        parse(apks, iconPx)
    }

    /** Reads several APKs shared together (a base APK and its splits) as one app. */
    suspend fun readMany(uris: List<Uri>, iconPx: Int): PackageToInstall = withContext(Dispatchers.IO) {
        clear()
        val dir = File(workRoot, System.nanoTime().toString()).apply { mkdirs() }
        ensureSpace(uris.sumOf { querySize(it) })
        val used = HashSet<String>()
        val apks = try {
            uris.mapIndexed { i, uri ->
                var name = (displayName(uri) ?: "part$i").substringAfterLast('/')
                    .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }
                while (!used.add(name)) name = "x_$name"
                copy(uri, File(dir, name)).also { if (!isApk(it)) throw ExportException(NOT_ONE_APP) }
            }
        } catch (e: ExportException) {
            throw e
        } catch (e: SecurityException) {
            throw ExportException(CANT_OPEN, e)
        } catch (e: IOException) {
            throw ExportException(CANT_OPEN, e)
        }
        parse(apks, iconPx)
    }

    private fun isApk(file: File): Boolean =
        runCatching { ZipFile(file).use { it.getEntry("AndroidManifest.xml") != null } }.getOrDefault(false)

    private fun parse(apks: List<File>, iconPx: Int): PackageToInstall {
        // Pick the base by name first so only one APK has to be parsed.
        val ordered = apks.sortedBy { f ->
            when {
                f.name.equals("base.apk", ignoreCase = true) -> 0
                !isSplitName(f.name) -> 1
                else -> 2
            }
        }
        val (baseFile, base) = ordered.firstNotNullOfOrNull { f -> archiveInfo(f, 0)?.takeIf { it.splitNames.isNullOrEmpty() }?.let { f to it } }
            ?: ordered.firstNotNullOfOrNull { f -> archiveInfo(f, 0)?.let { f to it } }
            ?: throw ExportException(NOT_AN_APK)
        val files = listOf(baseFile) + filterAbiSplits(apks - baseFile)

        val app = base.applicationInfo ?: throw ExportException(NOT_AN_APK)
        app.sourceDir = baseFile.path
        app.publicSourceDir = baseFile.path
        val label = runCatching { app.loadLabel(pm).toString() }.getOrNull()?.ifBlank { null } ?: base.packageName
        val icon = runCatching {
            app.loadIcon(pm).toBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()

        return withInstalledState(
            PackageToInstall(
                label = label,
                packageName = base.packageName,
                versionName = base.versionName.orEmpty().ifBlank { base.versionCodeCompat.toString() },
                versionCode = base.versionCodeCompat,
                minSdk = app.minSdkVersion,
                icon = icon,
                files = files,
                totalBytes = files.sumOf { it.length() },
                installedVersionName = null,
                installedVersionCode = null,
                signatureMatches = true,
                certs = emptySet(),
                baseFile = baseFile,
            ),
        )
    }

    /** Re-checks whether (and which version of) the app is on the phone, e.g. after an uninstall. */
    fun withInstalledState(pkg: PackageToInstall): PackageToInstall {
        val installed = runCatching { installedInfo(pkg.packageName) }.getOrNull()
            ?: return pkg.copy(installedVersionName = null, installedVersionCode = null, signatureMatches = true)
        // Only now is the (expensive) archive signature needed.
        val certs = pkg.certs.ifEmpty { archiveInfo(pkg.baseFile, certFlags)?.let(::certificates).orEmpty() }
        val installedCerts = certificates(installed)
        return pkg.copy(
            installedVersionName = installed.versionName,
            installedVersionCode = installed.versionCodeCompat,
            certs = certs,
            // Unknown certificates (rare) are treated as a match and left to the installer to judge.
            signatureMatches = certs.isEmpty() || installedCerts.isEmpty() || certs.any { it in installedCerts },
        )
    }

    fun abandon(sessionId: Int) {
        runCatching { pm.packageInstaller.abandonSession(sessionId) }
    }

    /**
     * Streams every file into a new session and commits it. Progress is reported as a fraction of
     * the bytes written; the outcome arrives later through [results].
     */
    suspend fun install(pkg: PackageToInstall, onProgress: (Float) -> Unit): Int = withContext(Dispatchers.IO) {
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg.packageName)
            setSize(pkg.totalBytes)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val sessionId = try {
            installer.createSession(params)
        } catch (e: IOException) {
            throw ExportException(CANT_INSTALL, e)
        }
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                var written = 0L
                val buffer = ByteArray(BUFFER)
                pkg.files.forEachIndexed { i, file ->
                    session.openWrite("${i}_${file.name}", 0, file.length()).use { out ->
                        file.inputStream().use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                written += n
                                onProgress(if (pkg.totalBytes > 0) written.toFloat() / pkg.totalBytes else 0f)
                            }
                        }
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(ACTION_INSTALL_RESULT)
                    .setPackage(context.packageName)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    // The system adds the status extras, so the intent must be mutable on Android 12+.
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
                committed = true
            }
        } catch (e: IOException) {
            throw ExportException(if (isNoSpace(e)) NO_SPACE else CANT_INSTALL, e)
        } catch (e: SecurityException) {
            throw ExportException(CANT_INSTALL, e)
        } finally {
            if (!committed) runCatching { installer.abandonSession(sessionId) }
        }
        sessionId
    }

    fun clear() {
        workRoot.deleteRecursively()
    }

    fun launchIntent(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    // ---------------------------------------------------------------------------------------------
    // Bundles
    // ---------------------------------------------------------------------------------------------

    /**
     * Extracts the APKs from an .apks / .xapk / .apkm zip in a single streaming pass. Returns null if
     * the zip turns out to be a plain APK. Paths are flattened (no zip-slip).
     */
    private fun streamBundle(uri: Uri, dir: File): List<File>? {
        val deviceAbis = Build.SUPPORTED_ABIS.map { it.replace('-', '_') }.toSet()
        val splits = ArrayList<File>()
        val standalones = ArrayList<File>()
        val used = HashSet<String>()
        try {
            val stream = resolver.openInputStream(uri) ?: throw ExportException(CANT_OPEN)
            ZipInputStream(BufferedInputStream(stream, BUFFER)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "AndroidManifest.xml") return null
                    if (entry.isDirectory || !entry.name.endsWith(".apk", ignoreCase = true)) continue
                    var name = entry.name.substringAfterLast('/').ifBlank { "split.apk" }
                    // Splits for CPUs this phone doesn't have are never needed — don't even write them.
                    val abi = abiOf(name)
                    if (abi != null && abi !in deviceAbis) continue
                    while (!used.add(name)) name = "x_$name"
                    val out = File(dir, name)
                    out.outputStream().use { zip.copyTo(it, BUFFER) }
                    // bundletool archives also carry fallback "standalones"; prefer the splits.
                    if (entry.name.startsWith("standalones/")) standalones += out else splits += out
                }
            }
        } catch (e: ZipException) {
            // Some zips can't be streamed (stored entries with data descriptors): fall back to a copy.
            dir.listFiles()?.forEach { it.delete() }
            return unpackFromCopy(uri, dir)
        }
        return if (splits.isNotEmpty()) {
            standalones.forEach { it.delete() }
            splits
        } else {
            standalones
        }
    }

    private fun unpackFromCopy(uri: Uri, dir: File): List<File>? {
        val source = copy(uri, File(dir, "source.zip"))
        ZipFile(source).use { zip ->
            if (zip.getEntry("AndroidManifest.xml") != null) return listOf(source.renameToApk("base.apk"))
            val entries = zip.entries().toList().filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            val chosen = entries.filterNot { it.name.startsWith("standalones/") }.ifEmpty { entries }
            val used = HashSet<String>()
            return chosen.map { entry ->
                var name = entry.name.substringAfterLast('/').ifBlank { "split.apk" }
                while (!used.add(name)) name = "x_$name"
                File(dir, name).also { out -> zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it, BUFFER) } } }
            }.also { source.delete() }
        }
    }

    private fun copy(uri: Uri, target: File): File {
        val input = resolver.openInputStream(uri) ?: throw ExportException(CANT_OPEN)
        input.use { i -> target.outputStream().use { i.copyTo(it, BUFFER) } }
        return target
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /** Keeps only the best CPU-architecture split for this device; other splits are left alone. */
    private fun filterAbiSplits(splits: List<File>): List<File> {
        val abis = splits.mapNotNull { abiOf(it.name) }.toSet()
        if (abis.isEmpty()) return splits
        val best = Build.SUPPORTED_ABIS.map { it.replace('-', '_') }.firstOrNull { it in abis }
            ?: throw ExportException("This app isn't made for this phone's processor")
        return splits.filter { abiOf(it.name).let { abi -> abi == null || abi == best } }
    }

    private fun abiOf(fileName: String): String? {
        val lower = fileName.lowercase().removeSuffix(".apk")
        return KNOWN_ABIS.firstOrNull { lower.endsWith(".$it") || lower.endsWith("_$it") || lower == it }
    }

    private fun isSplitName(name: String) = name.startsWith("split_") || name.startsWith("config.")

    private fun File.renameToApk(name: String): File {
        val target = File(parentFile, name)
        if (!renameTo(target)) throw ExportException(CANT_OPEN)
        return target
    }

    // ---------------------------------------------------------------------------------------------
    // Package info
    // ---------------------------------------------------------------------------------------------

    private val certFlags: Int
        @Suppress("DEPRECATION")
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    private fun archiveInfo(file: File, flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.path, flags)
        }
    }.getOrNull()

    private fun installedInfo(packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(certFlags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, certFlags)
        }

    @Suppress("DEPRECATION")
    private fun certificates(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        val sha = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().mapTo(HashSet()) { sig -> sha.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    private fun querySize(uri: Uri): Long = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        } ?: 0L
    }.getOrDefault(0L)

    private fun ensureSpace(needed: Long) {
        val free = runCatching { StatFs(context.cacheDir.path).availableBytes }.getOrNull() ?: return
        if (free < needed + SPACE_MARGIN) throw ExportException(NO_SPACE)
    }

    private fun isNoSpace(e: Throwable) =
        e.message.orEmpty().let { it.contains("ENOSPC") || it.contains("No space", ignoreCase = true) }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.shayor.apkextractor.INSTALL_RESULT"
        private const val BUFFER = 256 * 1024
        private const val SPACE_MARGIN = 50L * 1024 * 1024
        private val KNOWN_ABIS = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "riscv64", "mips64", "mips")

        const val NOT_AN_APK = "That file isn't an APK or APK bundle"
        const val CANT_OPEN = "Couldn't open that file"
        const val CANT_INSTALL = "Couldn't start the installation"
        const val NOT_ONE_APP = "Share one app file, or the APK parts of a single app"
        const val NO_SPACE = "Not enough storage space"

        private val _results = MutableSharedFlow<InstallResult>(extraBufferCapacity = 8)
        val results: SharedFlow<InstallResult> = _results.asSharedFlow()

        internal fun emit(result: InstallResult) {
            _results.tryEmit(result)
        }
    }
}

@Suppress("DEPRECATION")
private val PackageInfo.versionCodeCompat: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

/** Receives the PackageInstaller session status and forwards it to [ApkInstaller.results]. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val result = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm != null) {
                    InstallResult.NeedsConfirmation(sessionId, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else {
                    InstallResult.Failure(sessionId, "Couldn't show the install prompt")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallResult.Success(sessionId)
            else -> InstallResult.Failure(
                sessionId,
                friendlyMessage(status),
                cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED,
            )
        }
        ApkInstaller.emit(result)
    }

    private fun friendlyMessage(status: Int) = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation was cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation was blocked on this phone"
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "It conflicts with the version already installed. Uninstall that one first."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This app isn't compatible with this phone"
        PackageInstaller.STATUS_FAILURE_INVALID -> "The file is damaged or incomplete"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage space"
        else -> "Couldn't install the app"
    }
}
