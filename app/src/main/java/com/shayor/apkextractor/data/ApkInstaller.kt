package com.shayor.apkextractor.data

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Game data (an OBB file) inside an .xapk; copied to Android/obb/<package>/ when installing. */
@Immutable
data class ObbFile(
    /** Path of the entry inside the bundle. */
    val entry: String,
    /** File name in the OBB folder, e.g. main.120.com.example.game.obb. */
    val name: String,
    val size: Long,
)

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
    /** APKs plus game data. */
    val totalBytes: Long,
    /** Game data to copy next to the app (XAPK only); read from [source] only when installing. */
    val obbs: List<ObbFile> = emptyList(),
    /** Version of the same app already on the phone, or null if it isn't installed. */
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    /** False when the installed app was signed with a different key (install will fail). */
    val signatureMatches: Boolean,
    /**
     * True when the app isn't installed for this user but Android still keeps it: installed in
     * another profile or user (Secure Folder, Dual Messenger, a work profile), or uninstalled with
     * its data kept. That copy still decides which signature and version Android accepts.
     */
    val installedElsewhere: Boolean = false,
    internal val certs: Set<String>,
    internal val baseFile: File,
    internal val source: Uri? = null,
) {
    val isBundle get() = files.size > 1
    val hasObb get() = obbs.isNotEmpty()
    val obbBytes get() = obbs.sumOf { it.size.coerceAtLeast(0) }
    val isInstalled get() = installedVersionCode != null
    val isDowngrade get() = installedVersionCode != null && versionCode < installedVersionCode
    val needsNewerAndroid get() = minSdk > Build.VERSION.SDK_INT
}

/** Result of a PackageInstaller session, delivered by [InstallResultReceiver]. */
sealed interface InstallResult {
    val sessionId: Int

    data class NeedsConfirmation(override val sessionId: Int, val intent: Intent) : InstallResult
    data class Success(override val sessionId: Int) : InstallResult
    data class Failure(
        override val sessionId: Int,
        val message: String,
        val cancelled: Boolean = false,
        /** Android refused because of another copy of the app (signature or version). */
        val conflict: Boolean = false,
    ) : InstallResult
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
     * Reads the app inside [uri]. Bundles are read with random access when the source allows it (or
     * streamed in one pass otherwise): only the APKs for this device's CPU are extracted, and game
     * data (OBB) is just listed, then copied straight from the source when installing. Only the
     * base APK is parsed, and signatures are only checked when the app is already installed (that
     * check hashes the whole APK, so it is skipped when not needed).
     */
    suspend fun read(uri: Uri, iconPx: Int): PackageToInstall = withContext(Dispatchers.IO) {
        clear()
        val dir = File(workRoot, System.nanoTime().toString()).apply { mkdirs() }
        val bundle = try {
            val name = displayName(uri).orEmpty().lowercase()
            if (name.endsWith(".apk")) {
                querySize(uri).let { if (it > 0) ensureSpace(it) }
                Unpacked(listOf(copy(uri, File(dir, "base.apk"))))
            } else {
                // null = the zip is itself an APK (no recognisable extension).
                unpackBundle(uri, dir) ?: Unpacked(listOf(copy(uri, File(dir, "base.apk"))))
            }
        } catch (e: ExportException) {
            throw e
        } catch (e: SecurityException) {
            throw ExportException(CANT_OPEN, e)
        } catch (e: IOException) {
            throw ExportException(if (isNoSpace(e)) NO_SPACE else NOT_AN_APK, e)
        }
        if (bundle.apks.isEmpty()) throw ExportException(NOT_AN_APK)
        val pkg = parse(bundle.apks, iconPx)
        if (bundle.obbs.isEmpty()) pkg else pkg.copy(obbs = bundle.obbs, source = bundle.source).let { it.copy(totalBytes = it.totalBytes + it.obbBytes) }
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
                installedElsewhere = false,
                certs = emptySet(),
                baseFile = baseFile,
            ),
        )
    }

    /** Re-checks whether (and which version of) the app is on the phone, e.g. after an uninstall. */
    fun withInstalledState(pkg: PackageToInstall): PackageToInstall {
        val installed = runCatching { installedInfo(pkg.packageName) }.getOrNull()
            ?: return pkg.copy(
                installedVersionName = null, installedVersionCode = null,
                signatureMatches = true, installedElsewhere = false,
            )
        val here = installed.applicationInfo?.let { it.flags and ApplicationInfo.FLAG_INSTALLED != 0 } ?: true
        // Only now is the (expensive) archive signature needed.
        val certs = pkg.certs.ifEmpty { archiveInfo(pkg.baseFile, certFlags)?.let(::certificates).orEmpty() }
        val installedCerts = certificates(installed)
        return pkg.copy(
            installedVersionName = installed.versionName,
            installedVersionCode = installed.versionCodeCompat,
            installedElsewhere = !here,
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
        var written = 0L
        val report: (Long) -> Unit = { n ->
            written += n
            onProgress(if (pkg.totalBytes > 0) written.toFloat() / pkg.totalBytes else 0f)
        }
        // Game data first: it's the bulk of an XAPK, and a missing permission or space shows up
        // before anything is written to the session.
        if (pkg.hasObb) {
            try {
                copyObbs(pkg, report)
            } catch (e: ExportException) {
                throw e
            } catch (e: SecurityException) {
                throw ExportException(OBB_DENIED, e)
            } catch (e: IOException) {
                throw ExportException(if (isNoSpace(e)) NO_SPACE else OBB_FAILED, e)
            }
        }
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg.packageName)
            setSize(pkg.files.sumOf { it.length() })
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
                val buffer = ByteArray(BUFFER)
                pkg.files.forEachIndexed { i, file ->
                    session.openWrite("${i}_${file.name}", 0, file.length()).use { out ->
                        file.inputStream().use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                report(n.toLong())
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

    private class Unpacked(val apks: List<File>, val obbs: List<ObbFile> = emptyList(), val source: Uri? = null)

    private val deviceAbis by lazy { Build.SUPPORTED_ABIS.map { it.replace('-', '_') }.toSet() }

    /**
     * Extracts the APKs from an .apks / .xapk / .apkm zip. Returns null if the zip turns out to be a
     * plain APK. Random access when the source is a real file (only the needed entries are read at
     * all); a single streaming pass otherwise.
     */
    private fun unpackBundle(uri: Uri, dir: File): Unpacked? {
        openZip(uri)?.use { zip ->
            try {
                return unpackZip(zip, dir, uri) ?: return null
            } catch (_: ZipException) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
        return streamBundle(uri, dir)
    }

    /**
     * Opens [uri] as a random-access zip on its file descriptor, or null when the source can't seek
     * (a pipe or a network stream) or isn't a zip.
     */
    private fun openZip(uri: Uri): ChannelZip? = runCatching {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return@runCatching null
        // Pipes report no size and can't seek.
        if (pfd.statSize <= 0) {
            pfd.close()
            return@runCatching null
        }
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        ChannelZip(stream.channel, stream)
    }.getOrNull()

    /** Picks the APKs this device needs from [zip] and extracts them; lists game data without reading it. */
    private fun unpackZip(zip: ChannelZip, dir: File, source: Uri): Unpacked? {
        if (zip.getEntry("AndroidManifest.xml") != null) return null
        val splits = ArrayList<ChannelZip.Entry>()
        val standalones = ArrayList<ChannelZip.Entry>()
        val obbs = LinkedHashMap<String, ObbFile>()
        for (entry in zip.entries) {
            if (entry.isDirectory) continue
            val name = entry.name
            when {
                name.endsWith(".obb", ignoreCase = true) ->
                    obbName(name)?.let { obbs.putIfAbsent(it, ObbFile(name, it, entry.size)) }
                name.endsWith(".apk", ignoreCase = true) -> {
                    // Splits for CPUs this phone doesn't have are never needed: don't even read them.
                    val abi = abiOf(name.substringAfterLast('/'))
                    if (abi != null && abi !in deviceAbis) continue
                    // bundletool archives also carry fallback "standalones"; prefer the splits.
                    if (name.startsWith("standalones/")) standalones += entry else splits += entry
                }
            }
        }
        val used = HashSet<String>()
        val apks = splits.ifEmpty { standalones }.map { entry ->
            var name = entry.name.substringAfterLast('/').ifBlank { "split.apk" }
            while (!used.add(name)) name = "x_$name"
            File(dir, name).also { out -> zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it, BUFFER) } } }
        }
        return Unpacked(apks, obbs.values.toList(), source)
    }

    /**
     * The streaming fallback: one pass over the zip. Paths are flattened (no zip-slip). Game data
     * is only listed; its bytes are skipped, not written.
     */
    private fun streamBundle(uri: Uri, dir: File): Unpacked? {
        val splits = ArrayList<File>()
        val standalones = ArrayList<File>()
        val obbEntries = ArrayList<ZipEntry>()
        val used = HashSet<String>()
        try {
            val stream = resolver.openInputStream(uri) ?: throw ExportException(CANT_OPEN)
            ZipInputStream(BufferedInputStream(stream, BUFFER)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "AndroidManifest.xml") return null
                    if (entry.isDirectory) continue
                    if (entry.name.endsWith(".obb", ignoreCase = true)) {
                        obbEntries += entry // its size is known once the stream has moved past it
                        continue
                    }
                    if (!entry.name.endsWith(".apk", ignoreCase = true)) continue
                    var name = entry.name.substringAfterLast('/').ifBlank { "split.apk" }
                    val abi = abiOf(name)
                    if (abi != null && abi !in deviceAbis) continue
                    while (!used.add(name)) name = "x_$name"
                    val out = File(dir, name)
                    out.outputStream().use { zip.copyTo(it, BUFFER) }
                    if (entry.name.startsWith("standalones/")) standalones += out else splits += out
                }
            }
        } catch (e: ZipException) {
            // Some zips can't be streamed (stored entries with data descriptors): fall back to a copy.
            dir.listFiles()?.forEach { it.delete() }
            return unpackFromCopy(uri, dir)
        }
        val apks = if (splits.isNotEmpty()) {
            standalones.forEach { it.delete() }
            splits
        } else {
            standalones
        }
        val obbs = LinkedHashMap<String, ObbFile>()
        obbEntries.forEach { e -> obbName(e.name)?.let { obbs.putIfAbsent(it, ObbFile(e.name, it, e.size)) } }
        return Unpacked(apks, obbs.values.toList(), uri)
    }

    private fun unpackFromCopy(uri: Uri, dir: File): Unpacked? {
        val source = copy(uri, File(dir, "source.zip"))
        val unpacked = source.inputStream().let { ChannelZip(it.channel, it) }.use { unpackZip(it, dir, Uri.fromFile(source)) }
            ?: return Unpacked(listOf(source.renameToApk("base.apk")))
        // The copy is kept only while its game data still has to be installed.
        if (unpacked.obbs.isEmpty()) source.delete()
        return unpacked
    }

    // ---------------------------------------------------------------------------------------------
    // Game data (OBB)
    // ---------------------------------------------------------------------------------------------

    /** The file name only, so an entry can never be written outside the app's OBB folder. */
    private fun obbName(entry: String): String? =
        entry.substringAfterLast('/').substringAfterLast('\\').takeIf { it.length > 4 && !it.startsWith(".") }

    @Suppress("DEPRECATION")
    private fun obbDir(packageName: String) =
        File(Environment.getExternalStorageDirectory(), "Android/obb/$packageName")

    private fun partFile(dir: File, obb: ObbFile) = File(dir, "${obb.name}$PART")

    /**
     * Android 11+ lets apps allowed to install apps write to Android/obb; older versions need the
     * storage permission.
     */
    fun needsStoragePermission(pkg: PackageToInstall): Boolean =
        pkg.hasObb && Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /**
     * Copies the game data straight from the bundle into the app's OBB folder, as .part files that
     * [commitObbs] puts in place once the install succeeds. Parts already complete from an earlier
     * attempt (e.g. the install prompt was cancelled) aren't written again.
     */
    private suspend fun copyObbs(pkg: PackageToInstall, report: (Long) -> Unit) {
        val dir = obbDir(pkg.packageName)
        if (!dir.isDirectory && !dir.mkdirs()) throw ExportException(OBB_DENIED)
        val pending = pkg.obbs.filter { obb ->
            val done = obb.size >= 0 && partFile(dir, obb).length() == obb.size
            if (done) report(obb.size)
            !done
        }
        if (pending.isEmpty()) return
        val free = runCatching { StatFs(dir.path).availableBytes }.getOrNull()
        if (free != null && free < pending.sumOf { it.size.coerceAtLeast(0) } + SPACE_MARGIN) throw ExportException(NO_SPACE)

        val source = pkg.source ?: throw ExportException(OBB_FAILED)
        val buffer = ByteArray(BUFFER)
        val write: suspend (InputStream, ObbFile) -> Unit = { input, obb ->
            val out = try {
                partFile(dir, obb).outputStream()
            } catch (e: FileNotFoundException) {
                throw ExportException(OBB_DENIED, e)
            }
            out.use {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    it.write(buffer, 0, n)
                    report(n.toLong())
                }
            }
        }
        val zip = openZip(source)
        if (zip != null) {
            zip.use {
                for (obb in pending) {
                    val entry = it.getEntry(obb.entry) ?: throw ExportException(OBB_FAILED)
                    it.getInputStream(entry).use { input -> write(input, obb) }
                }
            }
        } else {
            val byEntry = pending.associateBy { it.entry }
            var left = byEntry.size
            val stream = resolver.openInputStream(source) ?: throw ExportException(CANT_OPEN)
            ZipInputStream(BufferedInputStream(stream, BUFFER)).use { input ->
                while (left > 0) {
                    val entry = input.nextEntry ?: break
                    val obb = byEntry[entry.name] ?: continue
                    write(input, obb)
                    left--
                }
            }
            if (left > 0) throw ExportException(OBB_FAILED)
        }
    }

    /** The app is installed: puts its game data in place (a rename, so instant). */
    fun commitObbs(pkg: PackageToInstall) {
        if (!pkg.hasObb) return
        val dir = obbDir(pkg.packageName)
        pkg.obbs.forEach { obb ->
            val part = partFile(dir, obb)
            if (!part.exists()) return@forEach
            val target = File(dir, obb.name)
            target.delete()
            part.renameTo(target)
        }
    }

    /** Removes game data copied for an install that won't happen. */
    fun discardObbs(pkg: PackageToInstall) {
        if (!pkg.hasObb) return
        val dir = obbDir(pkg.packageName)
        pkg.obbs.forEach { partFile(dir, it).delete() }
        dir.delete() // only if it is empty, i.e. it was created for this install
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

    /**
     * The copy of [packageName] Android knows about, including one that isn't installed for this
     * user (see [PackageToInstall.installedElsewhere]). This finds an app uninstalled with its data
     * kept; one installed only for another user or profile stays invisible to us, and shows up as a
     * conflict when installing ([InstallResult.Failure.conflict]).
     */
    private fun installedInfo(packageName: String): PackageInfo {
        val flags = certFlags or PackageManager.MATCH_UNINSTALLED_PACKAGES
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
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
        private const val PART = ".part"
        private val KNOWN_ABIS = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86", "riscv64", "mips64", "mips")

        const val NOT_AN_APK = "That file isn't an APK or APK bundle"
        const val CANT_OPEN = "Couldn't open that file"
        const val CANT_INSTALL = "Couldn't start the installation"
        const val NOT_ONE_APP = "Share one app file, or the APK parts of a single app"
        const val NO_SPACE = "Not enough storage space"
        const val OBB_DENIED = "Couldn't save the game data"
        const val OBB_FAILED = "Couldn't copy the game data"
        const val OBB_PERMISSION = "Storage access is needed for the game data"

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
                friendlyMessage(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()),
                cancelled = status == PackageInstaller.STATUS_FAILURE_ABORTED,
                conflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT,
            )
        }
        ApkInstaller.emit(result)
    }

    private fun friendlyMessage(status: Int, detail: String) = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation was cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation was blocked on this phone"
        PackageInstaller.STATUS_FAILURE_CONFLICT -> when {
            "UPDATE_INCOMPATIBLE" in detail || "SIGNATURE" in detail ->
                "Signed differently from another copy"
            "VERSION_DOWNGRADE" in detail -> "A newer copy is already on this phone"
            else -> "Conflicts with another copy of the app"
        }
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This app isn't compatible with this phone"
        PackageInstaller.STATUS_FAILURE_INVALID -> "The file is damaged or incomplete"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage space"
        else -> "Couldn't install the app"
    }
}
