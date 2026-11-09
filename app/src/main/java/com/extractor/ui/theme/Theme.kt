package com.extractor.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

    MaterialTheme(
        colorScheme = target,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}

private fun ColorScheme.toPureBlack() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF141517),
    surfaceContainer = Color(0xFF17181B),
    surfaceContainerHigh = Color(0xFF1D1F22),
    surfaceContainerHighest = Color(0xFF25272B),
)
