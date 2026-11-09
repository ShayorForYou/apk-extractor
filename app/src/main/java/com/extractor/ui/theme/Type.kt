package com.extractor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extractor.R

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans (SIL Open Font License 1.1), bundled as a variable font. */
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private val base = Typography()

private fun TextStyle.j(weight: FontWeight, size: Int? = null, tracking: Double = 0.0, line: Int? = null) = copy(
    fontFamily = Jakarta,
    fontWeight = weight,
    fontSize = size?.sp ?: fontSize,
    lineHeight = line?.sp ?: lineHeight,
    letterSpacing = tracking.sp,
)

val Typography = Typography(
    displayLarge = base.displayLarge.j(FontWeight.ExtraBold, tracking = -1.5),
    displayMedium = base.displayMedium.j(FontWeight.ExtraBold, tracking = -1.2),
    displaySmall = base.displaySmall.j(FontWeight.ExtraBold, size = 38, tracking = -1.2, line = 42),
    headlineLarge = base.headlineLarge.j(FontWeight.ExtraBold, size = 32, tracking = -1.0, line = 38),
    headlineMedium = base.headlineMedium.j(FontWeight.ExtraBold, size = 28, tracking = -0.8),
    headlineSmall = base.headlineSmall.j(FontWeight.Bold, size = 24, tracking = -0.5),
    titleLarge = base.titleLarge.j(FontWeight.Bold, size = 20, tracking = -0.3),
    titleMedium = base.titleMedium.j(FontWeight.Bold, size = 15, tracking = -0.1, line = 20),
    titleSmall = base.titleSmall.j(FontWeight.SemiBold, size = 14),
    bodyLarge = base.bodyLarge.j(FontWeight.Medium, size = 16),
    bodyMedium = base.bodyMedium.j(FontWeight.Medium, size = 14),
    bodySmall = base.bodySmall.j(FontWeight.Medium, size = 12, line = 16),
    labelLarge = base.labelLarge.j(FontWeight.Bold, size = 14),
    labelMedium = base.labelMedium.j(FontWeight.SemiBold, size = 12),
    labelSmall = base.labelSmall.j(FontWeight.SemiBold, size = 11, tracking = 0.4),
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
