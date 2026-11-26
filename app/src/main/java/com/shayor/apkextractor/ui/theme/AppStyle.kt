package com.shayor.apkextractor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.shayor.apkextractor.R

/*
 * The app's look: Plus Jakarta Sans, a cool blue-black or soft grey
 * canvas with a faint blue glow, flat cards edged by a hairline, small tracked caps for labels and a
 * vivid blue accent. [ApkExtractorTheme] provides it; most screens read it through [iosColors].
 */

@Immutable
data class AppPalette(
    val dark: Boolean,
    val background: Color,
    val glow: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val track: Color,
    val bad: Color,
)

val LightPalette = AppPalette(
    dark = false,
    background = Color(0xFFF5F6F9),
    glow = Color(0x100A84FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEEF3),
    hairline = Color(0x1A1C2433),
    textPrimary = Color(0xFF1B1E26),
    textSecondary = Color(0xFF5F6472),
    textMuted = Color(0xFF9DA2AE),
    accent = Color(0xFF0A84FF),
    track = Color(0x1F1C2433),
    bad = Color(0xFFF0443A),
)

val DarkPalette = AppPalette(
    dark = true,
    background = Color(0xFF0B0D12),
    glow = Color(0x1C3D9BFF),
    surface = Color(0xFF15181F),
    surfaceVariant = Color(0xFF1E222B),
    hairline = Color(0x1FFFFFFF),
    textPrimary = Color(0xFFEDEFF4),
    textSecondary = Color(0xFF9EA4B2),
    textMuted = Color(0xFF5F6575),
    accent = Color(0xFF3D9BFF),
    track = Color(0x24FFFFFF),
    bad = Color(0xFFFF5B50),
)

val LocalAppPalette = staticCompositionLocalOf { LightPalette }

val appColors: AppPalette
    @Composable @ReadOnlyComposable get() = LocalAppPalette.current

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/**
 * Plus Jakarta Sans, variable, trimmed to what the app shows to keep the APK small (79 KB -> 47 KB
 * compressed): Latin (Basic, Latin-1, Extended-A/B), combining marks, Vietnamese, general
 * punctuation, arrows and a few symbols, with the weight axis cut to the 400-700 the app uses.
 * Anything else (other scripts in app names) falls back to the system font. Regenerated from the
 * full font (in git history) with fonttools:
 *
 *     pyftsubset PlusJakartaSans.ttf --layout-features='*' --output-file=sub.ttf \
 *       --unicodes=U+0020-007E,U+00A0-024F,U+0300-036F,U+1EA0-1EF9,U+2000-206F,U+20AC,U+2122,U+2190-2199,U+2212
 *     fonttools varLib.instancer sub.ttf wght=400:700 -o plus_jakarta_sans.ttf
 */
val JakartaSans = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700))

