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

// The app's look: cool blue-black (dark) or soft grey (light) canvases, flat cards edged by a
// hairline, quiet blue-grey text and a vivid blue accent (or whatever the user picks).

val Ink = Color(0xFF0A84FF)
val InkLight = Color(0xFF6DB6FF)

/** The app's earlier periwinkle, kept as a preset. */
val Periwinkle = Color(0xFF5862B0)

val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3F0FF),
    onPrimaryContainer = Color(0xFF00336B),
    secondary = Color(0xFF5F6472),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEEF3),
    onSecondaryContainer = Color(0xFF1B1E26),
    tertiary = Ink,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3F0FF),
    onTertiaryContainer = Color(0xFF00336B),
    error = Color(0xFFF0443A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE6E4),
    onErrorContainer = Color(0xFF8A1A13),
    background = Color(0xFFF5F6F9),
    onBackground = Color(0xFF1B1E26),
    surface = Color(0xFFF5F6F9),
    onSurface = Color(0xFF1B1E26),
    surfaceVariant = Color(0xFFECEEF3),
    onSurfaceVariant = Color(0xFF5F6472),
    outline = Color(0xFFC9CCD6),
    outlineVariant = Color(0xFFE2E4EA),
    inverseSurface = Color(0xFF1B1E26),
    inverseOnSurface = Color(0xFFEDEFF4),
    inversePrimary = InkLight,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E8EE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6F9),
    surfaceContainer = Color(0xFFEFF1F5),
    surfaceContainerHigh = Color(0xFFECEEF3),
    surfaceContainerHighest = Color(0xFFDDE0E7),
    scrim = Color(0xFF000000),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF3D9BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF132A47),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF9EA4B2),
    onSecondary = Color(0xFF0B0D12),
    secondaryContainer = Color(0xFF1E222B),
    onSecondaryContainer = Color(0xFFEDEFF4),
    tertiary = InkLight,
    onTertiary = Color(0xFF0B2240),
    tertiaryContainer = Color(0xFF132A47),
    onTertiaryContainer = Color(0xFFD6E8FF),
    error = Color(0xFFFF5B50),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3B1513),
    onErrorContainer = Color(0xFFFFB4AE),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFEDEFF4),
    surface = Color(0xFF0B0D12),
    onSurface = Color(0xFFEDEFF4),
    surfaceVariant = Color(0xFF1E222B),
    onSurfaceVariant = Color(0xFF9EA4B2),
    outline = Color(0xFF353A47),
    outlineVariant = Color(0xFF262A33),
    inverseSurface = Color(0xFFEDEFF4),
    inverseOnSurface = Color(0xFF1B1E26),
    inversePrimary = Ink,
    surfaceTint = Color.Transparent,
    surfaceBright = Color(0xFF353A47),
    surfaceDim = Color(0xFF0B0D12),
    surfaceContainerLowest = Color(0xFF15181F),
    surfaceContainerLow = Color(0xFF15181F),
    surfaceContainer = Color(0xFF1A1E26),
    surfaceContainerHigh = Color(0xFF1E222B),
    surfaceContainerHighest = Color(0xFF2A2F3A),
    scrim = Color(0xFF000000),
)

/** Window background colors used before Compose draws its first frame. */
val LightWindowBackground = Color(0xFFF5F6F9)
val DarkWindowBackground = Color(0xFF0B0D12)

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
    groupedBackground = Color(0xFFF5F6F9),
    cell = Color.White,
    elevatedCell = Color.White,
    sheet = Color(0xFFF5F6F9),
    label = Color(0xFF1B1E26),
    secondaryLabel = Color(0xFF5F6472),
    tertiaryLabel = Color(0xFF9DA2AE),
    separator = Color(0x1F1C2433),
    fill = Color(0x2E1C2433),
    secondaryFill = Color(0x1F1C2433),
    tertiaryFill = Color(0x121C2433),
    green = Color(0xFF26B860),
    red = Color(0xFFF0443A),
    orange = Color(0xFFF29E1F),
    yellow = Color(0xFFF5C518),
    blue = Color(0xFF0A84FF),
    indigo = Color(0xFF5856D6),
    purple = Color(0xFFA25BDB),
    pink = Color(0xFFF0457A),
    teal = Color(0xFF13B5CB),
    gray = Color(0xFF9DA2AE),
    // Light glass: a thin white veil, a bright highlight along the top edge, and only a faint
    // edge below (a grey outline all round looks like plastic on a white canvas).
    glassTint = Color(0x59FFFFFF),
    glassRimTop = Color(0xE6FFFFFF),
    glassRimBottom = Color(0x0F1C2433),
)

val DarkIos = IosPalette(
    dark = true,
    groupedBackground = Color(0xFF0B0D12),
    cell = Color(0xFF15181F),
    elevatedCell = Color(0xFF1A1E26),
    sheet = Color(0xFF111419),
    label = Color(0xFFEDEFF4),
    secondaryLabel = Color(0xFF9EA4B2),
    tertiaryLabel = Color(0xFF636A7A),
    separator = Color(0x1FFFFFFF),
    fill = Color(0x2EFFFFFF),
    secondaryFill = Color(0x1FFFFFFF),
    tertiaryFill = Color(0x14FFFFFF),
    green = Color(0xFF3DD47A),
    red = Color(0xFFFF5B50),
    orange = Color(0xFFFFB13B),
    yellow = Color(0xFFFFD24A),
    blue = Color(0xFF3D9BFF),
    indigo = Color(0xFF6E6CF0),
    purple = Color(0xFFBF7BF5),
    pink = Color(0xFFFF5F8F),
    teal = Color(0xFF34DCEC),
    gray = Color(0xFF636A7A),
    glassTint = Color(0x8015181F),
    glassRimTop = Color(0x2EFFFFFF),
    glassRimBottom = Color(0x12FFFFFF),
)

/** "Pure black" darkens the canvas, rows and sheets too, for OLED screens. */
fun IosPalette.pureBlack() = copy(
    groupedBackground = Color.Black,
    cell = Color(0xFF0F1116),
    elevatedCell = Color(0xFF171A21),
    sheet = Color(0xFF0A0B0E),
    glassTint = Color(0x8C0F1116),
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
    AccentPreset("Blue", Ink),
    AccentPreset("Periwinkle", Periwinkle),
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
