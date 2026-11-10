package com.shayor.apkextractor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// "Ink" — a muted, greyed periwinkle taken from the app icon, on neutral surfaces.
// Saturation is kept deliberately low so the accent stays easy on the eyes.

val Ink = Color(0xFF5862B0)
val InkLight = Color(0xFFB3B8EE)

val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAEBF5),
    onPrimaryContainer = Color(0xFF262B5E),
    secondary = Color(0xFF5E6070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEEEF2),
    onSecondaryContainer = Color(0xFF1D1E26),
    tertiary = Ink,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEAEBF5),
    onTertiaryContainer = Color(0xFF262B5E),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9E3E0),
    onErrorContainer = Color(0xFF5C120C),
    background = Color(0xFFF6F6F8),
    onBackground = Color(0xFF1D1E26),
    surface = Color(0xFFF6F6F8),
    onSurface = Color(0xFF1D1E26),
    surfaceVariant = Color(0xFFEEEEF2),
    onSurfaceVariant = Color(0xFF6C6E7C),
    outline = Color(0xFFD0D1DA),
    outlineVariant = Color(0xFFE8E8EE),
    inverseSurface = Color(0xFF2A2B33),
    inverseOnSurface = Color(0xFFF2F2F5),
    inversePrimary = InkLight,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDEDEE4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBFC),
    surfaceContainer = Color(0xFFF0F0F4),
    surfaceContainerHigh = Color(0xFFE9E9EF),
    surfaceContainerHighest = Color(0xFFE1E1E8),
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFF20255A),
    primaryContainer = Color(0xFF2A2E4C),
    onPrimaryContainer = Color(0xFFE1E3FA),
    secondary = Color(0xFFC6C7D2),
    onSecondary = Color(0xFF2A2B33),
    secondaryContainer = Color(0xFF282930),
    onSecondaryContainer = Color(0xFFE8E8EE),
    tertiary = InkLight,
    onTertiary = Color(0xFF20255A),
    tertiaryContainer = Color(0xFF2A2E4C),
    onTertiaryContainer = Color(0xFFE1E3FA),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF3F1E1B),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF131418),
    onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF131418),
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF282930),
    onSurfaceVariant = Color(0xFF9C9DAB),
    outline = Color(0xFF44465A),
    outlineVariant = Color(0xFF292A33),
    inverseSurface = Color(0xFFE8E8EE),
    inverseOnSurface = Color(0xFF1C1D23),
    inversePrimary = Ink,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF383A44),
    surfaceDim = Color(0xFF131418),
    surfaceContainerLowest = Color(0xFF1A1B20),
    surfaceContainerLow = Color(0xFF1C1D23),
    surfaceContainer = Color(0xFF212228),
    surfaceContainerHigh = Color(0xFF282930),
    surfaceContainerHighest = Color(0xFF303139),
    scrim = Color(0xFF000000),
)

/** Window background colors used before Compose draws its first frame. */
val LightWindowBackground = Color(0xFFF6F6F8)
val DarkWindowBackground = Color(0xFF131418)

// -------------------------------------------------------------------------------------------------
// User accent
// -------------------------------------------------------------------------------------------------

/** A named, hand-picked accent. All are mid-tone and muted so every one stays easy on the eyes. */
data class AccentPreset(val name: String, val color: Color)

val AccentPresets = listOf(
    AccentPreset("Ink", Ink),
    AccentPreset("Ocean", Color(0xFF3E7CB1)),
    AccentPreset("Lagoon", Color(0xFF2F8A8A)),
    AccentPreset("Sage", Color(0xFF5E8A66)),
    AccentPreset("Amber", Color(0xFFB07A2A)),
    AccentPreset("Clay", Color(0xFFB5654A)),
    AccentPreset("Rose", Color(0xFFB4577A)),
    AccentPreset("Lavender", Color(0xFF8566B8)),
    AccentPreset("Slate", Color(0xFF5B6B7C)),
)

/**
 * Re-tints the accent roles of this scheme from a single seed colour, keeping the neutral
 * surfaces. Light and dark get their own variants, nudged so text on the accent stays legible
 * however bright or dark the chosen colour is.
 */
fun ColorScheme.withAccent(seed: Color?, dark: Boolean): ColorScheme {
    if (seed == null) return this
    val s = seed.copy(alpha = 1f)
    return if (!dark) {
        val primary = when {
            s.luminance() > 0.40f -> lerp(s, Color.Black, 0.40f) // too light to carry white text
            s.luminance() < 0.03f -> lerp(s, Color.White, 0.25f) // near-black: lift it a little
            else -> s
        }
        val container = lerp(s, Color.White, 0.86f)
        val onContainer = lerp(s, Color.Black, 0.62f)
        copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            tertiary = primary,
            onTertiary = Color.White,
            tertiaryContainer = container,
            onTertiaryContainer = onContainer,
            inversePrimary = lerp(s, Color.White, 0.45f),
        )
    } else {
        val primary = lerp(s, Color.White, if (s.luminance() < 0.15f) 0.50f else 0.32f)
        val onPrimary = lerp(s, Color.Black, 0.72f)
        val container = lerp(s, background, 0.70f)
        val onContainer = lerp(s, Color.White, 0.82f)
        copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = container,
            onTertiaryContainer = onContainer,
            inversePrimary = s,
        )
    }
}
