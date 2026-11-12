package com.shayor.apkextractor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The iOS type ramp (Large Title 34, Title 1–3, Headline 17 semibold, Body 17, Subheadline 15,
 * Footnote 13, Captions 12/11) on the system sans-serif, a neo-grotesque like SF Pro. Large sizes
 * are tracked in slightly, as SF's display cut is.
 */

private val Base = TextStyle(
    fontFamily = FontFamily.Default,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private fun ios(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = Base.copy(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displayLarge = ios(48, 54, FontWeight.Bold, -1.0),
    displayMedium = ios(40, 46, FontWeight.Bold, -0.8),
    displaySmall = ios(36, 42, FontWeight.Bold, -0.7),
    headlineLarge = ios(34, 41, FontWeight.Bold, -0.6), // Large Title
    headlineMedium = ios(28, 34, FontWeight.Bold, -0.4), // Title 1
    headlineSmall = ios(22, 28, FontWeight.Bold, -0.3), // Title 2
    titleLarge = ios(20, 25, FontWeight.SemiBold, -0.2), // Title 3
    titleMedium = ios(17, 22, FontWeight.SemiBold, -0.2), // Headline
    titleSmall = ios(15, 20, FontWeight.SemiBold, -0.1), // Subheadline, emphasized
    bodyLarge = ios(17, 22, FontWeight.Normal, -0.2), // Body
    bodyMedium = ios(15, 20, FontWeight.Normal, -0.1), // Subheadline
    bodySmall = ios(13, 18, FontWeight.Normal), // Footnote
    labelLarge = ios(15, 20, FontWeight.SemiBold, -0.1), // Buttons
    labelMedium = ios(12, 16, FontWeight.Medium), // Caption 1
    labelSmall = ios(11, 13, FontWeight.Medium, 0.1), // Caption 2
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)
