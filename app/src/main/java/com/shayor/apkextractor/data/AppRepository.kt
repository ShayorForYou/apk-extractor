package com.shayor.apkextractor.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

class AppRepository(context: Context) {

    private val pm = context.applicationContext.packageManager
    private val parallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    private val loader = Dispatchers.IO.limitedParallelism(parallelism)

    /** Loads every installed app. Label loading is the slow part, so it is spread over several threads. */
    suspend fun loadAll(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packages = installedPackages()
        if (packages.isEmpty()) return@withContext emptyList()
        val chunkSize = (packages.size / (parallelism * 2)).coerceAtLeast(12)
        coroutineScope {
            packages.chunked(chunkSize)
                .map { chunk -> async(loader) { chunk.mapNotNull(::toAppInfo) } }
                .awaitAll()
                .flatten()
        }
    }

    /** Loads a single app, or null if it is not installed (anymore). */
    suspend fun load(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        try {
            toAppInfo(pm.packageInfoCompat(packageName))
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun installedPackages(): List<PackageInfo> = try {
        pm.installedPackagesCompat()
    } catch (_: RuntimeException) {
        // On devices with a huge number of apps the binder reply can overflow
        // (TransactionTooLargeException wrapped in a RuntimeException). Fall back
        // to the lighter application list and query packages one by one.
        @Suppress("DEPRECATION")
        val apps = runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList())
        apps.mapNotNull { ai ->
            try {
                pm.packageInfoCompat(ai.packageName)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun toAppInfo(pi: PackageInfo): AppInfo? {
        val ai = pi.applicationInfo ?: return null
        val sourceDir = ai.sourceDir ?: return null
        val base = File(sourceDir)
        if (!base.exists()) return null // e.g. some APEX modules / overlays without an APK
        val splits = ai.splitSourceDirs?.filterNotNull().orEmpty()
        val baseSize = base.length()
        val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault("")
            .replace(WHITESPACE, " ")
            .trim()
            .ifEmpty { pi.packageName }

        return AppInfo(
            packageName = pi.packageName,
            label = label,
            versionName = pi.versionName.orEmpty().trim(),
            versionCode = PackageInfoCompat.getLongVersionCode(pi),
            isSystem = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            isEnabled = ai.enabled,
            sourceDir = sourceDir,
            splitSourceDirs = splits,
            baseSizeBytes = baseSize,
            sizeBytes = baseSize + splits.sumOf { File(it).length() },
            firstInstallTime = pi.firstInstallTime,
            lastUpdateTime = pi.lastUpdateTime,
            minSdk = ai.minSdkVersion,
            targetSdk = ai.targetSdkVersion,
        )
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

@Suppress("DEPRECATION")
internal fun PackageManager.installedPackagesCompat(): List<PackageInfo> =
    if (Build.VERSION.SDK_INT >= 33) getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
    else getInstalledPackages(0)

@Suppress("DEPRECATION")
internal fun PackageManager.packageInfoCompat(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= 33) getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    else getPackageInfo(packageName, 0)

@Suppress("DEPRECATION")
internal fun PackageManager.applicationInfoCompat(packageName: String): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= 33) getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    else getApplicationInfo(packageName, 0)
