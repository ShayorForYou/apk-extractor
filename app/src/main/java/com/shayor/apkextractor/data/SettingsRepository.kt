package com.shayor.apkextractor.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Settings are backed by SharedPreferences so they can be read synchronously at
 * startup — this lets the very first frame use the right theme (no light/dark flash).
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun update(transform: (Settings) -> Settings) {
        val old = _settings.value
        val new = _settings.updateAndGet(transform)
        if (new != old) write(new)
    }

    private fun read() = Settings(
        themeMode = enumOr(prefs.getString(K_THEME, null), ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean(K_DYNAMIC, false),
        pureBlack = prefs.getBoolean(K_BLACK, false),
        outputTreeUri = prefs.getString(K_TREE, null),
        outputFolderName = prefs.getString(K_TREE_NAME, null),
        splitMode = enumOr(prefs.getString(K_SPLIT, null), SplitMode.BUNDLE),
        nameFormat = enumOr(prefs.getString(K_NAME, null), NameFormat.LABEL_VERSION),
        filter = enumOr(prefs.getString(K_FILTER, null), AppFilter.USER),
        packageType = enumOr(prefs.getString(K_TYPE, null), PackageType.ALL),
        sortBy = enumOr(prefs.getString(K_SORT, null), SortBy.NAME),
        sortAscending = prefs.getBoolean(K_ASC, true),
        showTips = prefs.getBoolean(K_TIPS, true),
        accent = if (prefs.contains(K_ACCENT)) prefs.getInt(K_ACCENT, 0) else null,
    )

    private fun write(s: Settings) = prefs.edit {
        putString(K_THEME, s.themeMode.name)
        putBoolean(K_DYNAMIC, s.dynamicColor)
        putBoolean(K_BLACK, s.pureBlack)
        putString(K_TREE, s.outputTreeUri)
        putString(K_TREE_NAME, s.outputFolderName)
        putString(K_SPLIT, s.splitMode.name)
        putString(K_NAME, s.nameFormat.name)
        putString(K_FILTER, s.filter.name)
        putString(K_TYPE, s.packageType.name)
        putString(K_SORT, s.sortBy.name)
        putBoolean(K_ASC, s.sortAscending)
        putBoolean(K_TIPS, s.showTips)
        if (s.accent != null) putInt(K_ACCENT, s.accent) else remove(K_ACCENT)
    }

    companion object {
        private const val K_THEME = "theme"
        // v2: the brand palette is now the default; wallpaper colors are opt-in.
        private const val K_DYNAMIC = "dynamic_color_v2"
        private const val K_BLACK = "pure_black"
        private const val K_TREE = "output_tree"
        private const val K_TREE_NAME = "output_tree_name"
        // v3: the default is BUNDLE again. It keeps the original signature, so a saved copy (and every
        // auto-backup) can later be installed as an update over the store version; a merged APK is
        // re-signed and can't. A new key moves everyone to it once, as each save writes all settings.
        private const val K_SPLIT = "split_mode_v3"
        private const val K_NAME = "name_format"
        private const val K_FILTER = "filter"
        private const val K_TYPE = "package_type"
        private const val K_SORT = "sort"
        private const val K_ASC = "sort_asc"
        private const val K_TIPS = "show_tips"
        private const val K_ACCENT = "accent"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
            name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
    }
}
