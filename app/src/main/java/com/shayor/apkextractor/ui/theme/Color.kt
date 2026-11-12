package com.shayor.apkextractor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// Neutrals follow Apple's iOS system colours (grouped backgrounds, labels, fills, separators), so
// the app reads as an iOS app. The accent stays the app's own: "Ink", a muted periwinkle taken from
// the app icon, or whatever the user picks.

val Ink = Color(0xFF5862B0)
val InkLight = Color(0xFFB3B8EE)

val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAEBF5),
    onPrimaryContainer = Color(0xFF262B5E),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Ink,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEAEBF5),
    onTertiaryContainer = Color(0xFF262B5E),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF8A1A13),
    background = Color(0xFFF2F2F7), // systemGroupedBackground
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF000000), // label
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF8A8A8E), // secondaryLabel
    outline = Color(0xFFC7C7CC), // systemGray3
    outlineVariant = Color(0xFFD8D8DC), // separator
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = InkLight,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF), // secondarySystemGroupedBackground (cells)
    surfaceContainerLow = Color(0xFFF2F2F7), // sheets
    surfaceContainer = Color(0xFFEFEFF4),
    surfaceContainerHigh = Color(0xFFE9E9EE), // ~tertiarySystemFill on white
    surfaceContainerHighest = Color(0xFFD1D1D6), // systemGray4
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    // iOS keeps accents saturated in dark mode, with white text on filled buttons.
    primary = Color(0xFF7C86E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A2E4C),
    onPrimaryContainer = Color(0xFFE1E3FA),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = InkLight,
    onTertiary = Color(0xFF20255A),
    tertiaryContainer = Color(0xFF2A2E4C),
    onTertiaryContainer = Color(0xFFE1E3FA),
    error = Color(0xFFFF453A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3B1513),
    onErrorContainer = Color(0xFFFFB4AE),
    background = Color(0xFF000000), // systemGroupedBackground
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F), // secondaryLabel
    outline = Color(0xFF48484A), // systemGray3
    outlineVariant = Color(0xFF38383A), // separator
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Ink,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF636366),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF1C1C1E), // secondarySystemGroupedBackground (cells)
    surfaceContainerLow = Color(0xFF1C1C1E), // sheets
    surfaceContainer = Color(0xFF242426),
    surfaceContainerHigh = Color(0xFF2C2C2E), // tertiarySystemGroupedBackground
    surfaceContainerHighest = Color(0xFF3A3A3C),
    scrim = Color(0xFF000000),
)

/** Window background colors used before Compose draws its first frame. */
val LightWindowBackground = Color(0xFFF2F2F7)
val DarkWindowBackground = Color(0xFF000000)

/**
 * iOS colours that Material's [ColorScheme] has no slot for. Read with [iosColors].
 *
 * @property cell background of a row in an inset grouped list. Inside a sheet, use [LocalCellColor].
 * @property elevatedCell a row inside a sheet (one step lighter than the sheet in dark mode).
 */
@Immutable
data class IosPalette(
    val dark: Boolean,
    val groupedBackground: Color,
    val cell: Color,
    val elevatedCell: Color,
    val sheet: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    /** systemFill / secondarySystemFill / tertiarySystemFill: translucent greys for controls. */
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val blue: Color,
    val indigo: Color,
    val purple: Color,
    val pink: Color,
    val teal: Color,
    val gray: Color,
    /** Glass: tint laid over the blurred backdrop, and the light catching its rim. */
    val glassTint: Color,
    val glassRimTop: Color,
    val glassRimBottom: Color,
)

val LightIos = IosPalette(
    dark = false,
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color.White,
    elevatedCell = Color.White,
    sheet = Color(0xFFF2F2F7),
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    blue = Color(0xFF007AFF),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFAF52DE),
    pink = Color(0xFFFF2D55),
    teal = Color(0xFF30B0C7),
    gray = Color(0xFF8E8E93),
    glassTint = Color(0xB8FAFAFC),
    glassRimTop = Color(0xCCFFFFFF),
    glassRimBottom = Color(0x33FFFFFF),
)

val DarkIos = IosPalette(
    dark = true,
    groupedBackground = Color.Black,
    cell = Color(0xFF1C1C1E),
    elevatedCell = Color(0xFF2C2C2E),
    sheet = Color(0xFF1C1C1E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    blue = Color(0xFF0A84FF),
    indigo = Color(0xFF5E5CE6),
    purple = Color(0xFFBF5AF2),
    pink = Color(0xFFFF375F),
    teal = Color(0xFF40C8E0),
    gray = Color(0xFF8E8E93),
    glassTint = Color(0xB01E1E20),
    glassRimTop = Color(0x40FFFFFF),
    glassRimBottom = Color(0x0DFFFFFF),
)

/** "Pure black" darkens the rows and sheets too, for OLED screens. */
fun IosPalette.pureBlack() = copy(
    cell = Color(0xFF111113),
    elevatedCell = Color(0xFF1C1C1E),
    sheet = Color(0xFF0B0B0C),
    glassTint = Color(0xB8080809),
)

val LocalIosPalette = staticCompositionLocalOf { LightIos }

/** Background for grouped rows; sheets provide [IosPalette.elevatedCell] so rows stand out there. */
val LocalCellColor = compositionLocalOf { Color.Unspecified }

val iosColors: IosPalette
    @Composable @ReadOnlyComposable get() = LocalIosPalette.current

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
        // Only lifted a little (iOS dark accents stay vivid); white text unless it ends up very light.
        val primary = lerp(s, Color.White, if (s.luminance() < 0.15f) 0.28f else 0.10f)
        val onPrimary = if (primary.luminance() > 0.5f) Color(0xFF111111) else Color.White
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
