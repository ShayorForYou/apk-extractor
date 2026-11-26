package com.shayor.apkextractor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Plus Jakarta Sans. It sets wider than SF, so sizes sit a step under the
 * iOS ramp; only display and title sizes are tracked in, everything smaller is left open.
 */

private val Base = TextStyle(
    fontFamily = JakartaSans,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private fun type(size: Double, line: Double, weight: FontWeight, tracking: Double = 0.0) = Base.copy(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displayLarge = type(46.0, 52.0, FontWeight.SemiBold, -1.2),
    displayMedium = type(40.0, 46.0, FontWeight.SemiBold, -1.0),
    displaySmall = type(34.0, 40.0, FontWeight.SemiBold, -0.8),
    headlineLarge = type(32.0, 38.0, FontWeight.Bold, -0.8), // large title
    headlineMedium = type(28.0, 34.0, FontWeight.Bold, -0.6), // page titles
    headlineSmall = type(21.0, 27.0, FontWeight.SemiBold, -0.3),
    titleLarge = type(19.0, 24.0, FontWeight.SemiBold, -0.2),
    titleMedium = type(16.0, 21.0, FontWeight.SemiBold, -0.1),
    titleSmall = type(14.0, 19.0, FontWeight.SemiBold),
    bodyLarge = type(15.0, 21.0, FontWeight.Normal), // rows
    bodyMedium = type(14.0, 19.5, FontWeight.Normal),
    bodySmall = type(12.5, 17.0, FontWeight.Normal, 0.05), // secondary lines
    labelLarge = type(14.0, 19.0, FontWeight.SemiBold), // buttons
    labelMedium = type(12.0, 16.0, FontWeight.Medium, 0.1),
    labelSmall = type(10.5, 13.0, FontWeight.SemiBold, 0.4),
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)
