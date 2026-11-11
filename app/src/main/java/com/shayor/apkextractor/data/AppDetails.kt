package com.shayor.apkextractor.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Details that are too costly to read for every app in the list, loaded when one app is opened. */
@Immutable
data class AppDetails(
    /** Who installed the app (e.g. "Google Play Store"), "Preinstalled", or null if unknown. */
    val installer: String?,
    /** SHA-256 fingerprints of the signing certificates, formatted as AA:BB:…. */
    val signatures: List<String>,
)

object AppDetailsLoader {

    suspend fun load(context: Context, app: AppInfo): AppDetails? = withContext(Dispatchers.IO) {
        val pm = context.applicationContext.packageManager
        val info = runCatching { pm.packageInfo(app.packageName, CERT_FLAG) }.getOrNull()
            ?: return@withContext null
        AppDetails(
            installer = installerOf(pm, app),
            signatures = fingerprints(info),
        )
    }

    private fun installerOf(pm: PackageManager, app: AppInfo): String? {
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(app.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(app.packageName)
            }
        }.getOrNull()
        if (installer == null) return if (app.isSystem) "Preinstalled" else null
        return KNOWN_INSTALLERS[installer]
            ?: runCatching { pm.getApplicationLabel(pm.applicationInfoCompat(installer)).toString() }.getOrNull()
            ?: installer
    }

    @Suppress("DEPRECATION")
    private fun fingerprints(info: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else {
            info.signatures
        }
        val sha = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { sig ->
            sha.digest(sig.toByteArray()).joinToString(":") { "%02X".format(it) }
        }.distinct()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.packageInfo(pkg: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else getPackageInfo(pkg, flags)

    @Suppress("DEPRECATION")
    private val CERT_FLAG =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    private val KNOWN_INSTALLERS = mapOf(
        "com.android.vending" to "Google Play Store",
        "com.google.android.packageinstaller" to "Package installer",
        "com.android.packageinstaller" to "Package installer",
        "com.amazon.venezia" to "Amazon Appstore",
        "com.sec.android.app.samsungapps" to "Galaxy Store",
        "com.huawei.appmarket" to "AppGallery",
        "com.xiaomi.market" to "Xiaomi GetApps",
        "org.fdroid.fdroid" to "F-Droid",
    )

}
