package com.extractor

import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.extractor.data.SettingsRepository
import com.extractor.data.ThemeMode
import com.extractor.ui.ApkExtractorApp
import com.extractor.ui.InstallViewModel
import com.extractor.ui.MainViewModel
import com.extractor.ui.theme.ApkExtractorTheme
import com.extractor.ui.theme.DarkWindowBackground
import com.extractor.ui.theme.LightWindowBackground

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val installVm: InstallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val settingsRepo = SettingsRepository.get(this)

        // Paint the window in the chosen theme before the first Compose frame (no flash).
        val initial = settingsRepo.settings.value
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val startDark = when (initial.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val windowColor = when {
            startDark && initial.pureBlack -> Color.BLACK
            startDark -> DarkWindowBackground.toArgb()
            else -> LightWindowBackground.toArgb()
        }
        window.setBackgroundDrawable(ColorDrawable(windowColor))

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        holdSplashUntilReady()

        // Opened or shared from another app: go straight to the installer. Not on recreation,
        // so the same file isn't offered again after e.g. a configuration change.
        if (savedInstanceState == null) handleIncoming(intent)

        // No custom splash exit animation on purpose: the system's own exit runs in the system
        // process, so it stays smooth even while the app draws its first full frame.

        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Tell the system the app's own theme, so the next launch's splash matches it
            // (otherwise it follows the device setting and flashes the wrong colours).
            LaunchedEffect(settings.themeMode) { applyAppNightMode(settings.themeMode) }

            // Status/navigation bar icons follow the app theme, not the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }

            ApkExtractorTheme(
                darkTheme = dark,
                dynamicColor = false, // the brand palette is the design
                pureBlack = settings.pureBlack,
                accent = settings.accent?.let { androidx.compose.ui.graphics.Color(it) },
            ) {
                ApkExtractorApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        val uris = incomingUris(intent ?: return)
        if (uris.isNotEmpty()) installVm.openShared(uris)
    }

    /** The files in a VIEW / SEND / SEND_MULTIPLE intent. */
    @Suppress("DEPRECATION")
    private fun incomingUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri,
        )
        Intent.ACTION_SEND_MULTIPLE -> (
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            )?.filterNotNull()?.takeIf { it.isNotEmpty() }
            ?: intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        else -> emptyList()
    }

    /**
     * Holds the first frame (and so the splash) until the app list and its first icons are
     * ready, so the home screen appears complete. Capped so a slow device never waits long.
     */
    private fun holdSplashUntilReady() {
        val content = findViewById<View>(android.R.id.content)
        val start = SystemClock.uptimeMillis()
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    val ready = vm.ready.value || SystemClock.uptimeMillis() - start > SPLASH_MAX_HOLD_MS
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                    return ready
                }
            },
        )
    }

    /**
     * Only calls the system when the theme actually changed: setting it triggers a
     * configuration change, which would cost a full recomposition during startup.
     */
    private fun applyAppNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (prefs.getString(KEY_APPLIED_NIGHT_MODE, null) == mode.name) return
        val uiMode = getSystemService(UiModeManager::class.java) ?: return
        prefs.edit().putString(KEY_APPLIED_NIGHT_MODE, mode.name).apply()
        uiMode.setApplicationNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private companion object {
        const val KEY_APPLIED_NIGHT_MODE = "applied_night_mode"
        const val SPLASH_MAX_HOLD_MS = 1_500L
        val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
