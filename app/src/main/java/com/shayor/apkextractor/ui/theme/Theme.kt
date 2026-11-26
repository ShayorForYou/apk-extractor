package com.shayor.apkextractor.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.shayor.apkextractor.ui.components.IosHighlight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkExtractorTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val target = remember(darkTheme, dynamicColor, pureBlack, accent) {
        val scheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            darkTheme -> DarkColors
            else -> LightColors
        }
        val base = if (darkTheme && pureBlack) scheme.toPureBlack() else scheme
        base.withAccent(accent, darkTheme)
    }
    val ios = remember(darkTheme, pureBlack) {
        when {
            !darkTheme -> LightIos
            pureBlack -> DarkIos.pureBlack()
            else -> DarkIos
        }
    }

    // The app palette, following the user's accent and Pure Black.
    val palette = remember(ios, target) {
        (if (ios.dark) DarkPalette else LightPalette).copy(
            background = ios.groupedBackground,
            surface = ios.cell,
            accent = target.primary,
        )
    }

    MaterialTheme(
        colorScheme = target,
        typography = Typography,
        shapes = Shapes,
    ) {
        CompositionLocalProvider(
            LocalIosPalette provides ios,
            LocalAppPalette provides palette,
            LocalCellColor provides ios.cell,
            // Pressed rows light up like iOS cells (no ripples). Overscroll stays the platform's own:
            // it is drawn on the GPU and never fights the pager's or the list's own flings.
            LocalIndication provides IosHighlight,
            // For the few Material components that still ripple: keep it a faint highlight.
            LocalRippleConfiguration provides RippleConfiguration(
                color = ios.label,
                rippleAlpha = RippleAlpha(0.06f, 0.06f, 0.04f, 0.08f),
            ),
            content = content,
        )
    }
}

private fun ColorScheme.toPureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color(0xFF0F1116),
    surfaceContainerLow = Color(0xFF0A0B0E),
    surfaceContainer = Color(0xFF13151B),
    surfaceContainerHigh = Color(0xFF171A21),
    surfaceContainerHighest = Color(0xFF22262F),
)
