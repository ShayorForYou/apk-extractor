package com.shayor.apkextractor.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val baseSizeBytes: Long,
    val sizeBytes: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val minSdk: Int,
    val targetSdk: Int,
) {
    val isSplit: Boolean get() = splitSourceDirs.isNotEmpty()

    /** Changes whenever the app is updated, so cached icons are refreshed. */
    val iconKey: String get() = "$packageName@$lastUpdateTime"

    val displayVersion: String
        get() = versionName.ifBlank { versionCode.toString() }
}

enum class AppFilter { USER, SYSTEM, ALL }

/** Which kind of package the app list shows: everything, only split apps, or only single-APK apps. */
enum class PackageType(val label: String, val description: String) {
    ALL("All Types", "Every app"),
    SPLIT("Split APKs", "Apps installed in several parts"),
    SINGLE("Single APKs", "Apps installed as one file"),
}

enum class SortBy(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    INSTALLED("Install date"),
    UPDATED("Last updated"),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SplitMode(val label: String, val description: String) {
    BUNDLE("APKS Bundle", "Keeps the original signature, so it can update the store version. Install it from the Install tab"),
    MERGE("Single APK", "One file that installs anywhere, re-signed"),
    BASE_ONLY("Base APK only", "For inspection. May not install"),
}

enum class NameFormat(val label: String) {
    LABEL_VERSION("App name + version"),
    PACKAGE_VERSION("Package name + version"),
    LABEL_PACKAGE_VERSION("App name + package + version"),
}

@Immutable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val outputTreeUri: String? = null,
    val outputFolderName: String? = null,
    val splitMode: SplitMode = SplitMode.BUNDLE,
    val nameFormat: NameFormat = NameFormat.LABEL_VERSION,
    val filter: AppFilter = AppFilter.USER,
    val packageType: PackageType = PackageType.ALL,
    val sortBy: SortBy = SortBy.NAME,
    val sortAscending: Boolean = true,
    val showTips: Boolean = true,
    /** Custom accent (ARGB); null = the default "Ink" accent. */
    val accent: Int? = null,
)

/** A file that was written successfully and can be shared via [uri]. */
@Immutable
data class ExportedFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val location: String,
)

/** An export failure with a message that is safe to show to the user. */
class ExportException(message: String, cause: Throwable? = null) : Exception(message, cause)
