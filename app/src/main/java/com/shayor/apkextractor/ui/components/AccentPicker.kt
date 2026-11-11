package com.shayor.apkextractor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shayor.apkextractor.ui.theme.AccentPresets

/**
 * Accent swatches plus a "custom" swatch that opens a hue / intensity picker (with an exact hex field).
 * [selected] is the stored ARGB value, or null for the default (first preset).
 */
@Composable
fun AccentPicker(selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val presetArgbs = AccentPresets.map { it.color.toArgb() }
    val isCustom = selected != null && selected !in presetArgbs

    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccentPresets.forEachIndexed { i, preset ->
            val active = if (i == 0) selected == null || selected == presetArgbs[0] else selected == presetArgbs[i]
            Swatch(
                fill = SolidColor(preset.color),
                active = active,
                label = preset.name,
                // The first preset is the default, stored as "no custom accent".
                onClick = { onSelect(if (i == 0) null else presetArgbs[i]) },
            )
        }
        Swatch(
            fill = if (isCustom) SolidColor(Color(selected!!)) else RainbowBrush,
            active = isCustom,
            label = "Custom color",
            onClick = { showCustom = true },
            icon = if (isCustom) null else Icons.Rounded.Add,
        )
    }

    if (showCustom) {
        HexColorDialog(
            initial = selected?.let { Color(it) },
            onDismiss = { showCustom = false },
            onApply = {
                onSelect(it.toArgb())
                showCustom = false
            },
        )
    }
}

private val UppercaseTransformation = VisualTransformation { text ->
    TransformedText(AnnotatedString(text.text.uppercase()), OffsetMapping.Identity)
}

private val RainbowBrush = Brush.sweepGradient(
    listOf(
        Color(0xFFE57373), Color(0xFFFFB74D), Color(0xFFFFF176), Color(0xFF81C784),
        Color(0xFF4FC3F7), Color(0xFF9575CD), Color(0xFFF06292), Color(0xFFE57373),
    ),
)

@Composable
private fun Swatch(
    fill: Brush,
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (active) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = label
                selected = active
            }
            .padding(if (active) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        when {
            active -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            icon != null -> Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Custom colour: hue + intensity sliders, live preview, exact hex underneath
// -------------------------------------------------------------------------------------------------

/**
 * The sliders only produce colours in a readable band (no near-white / near-black / neon), because
 * the accent fills buttons and pills that carry text. A typed hex code is taken exactly as entered.
 */
private fun accentFrom(hue: Float, intensity: Float): Color =
    Color.hsl(hue, saturation = 0.22f + 0.66f * intensity, lightness = 0.60f - 0.12f * intensity)

private fun hueIntensityOf(color: Color): Pair<Float, Float> {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    return hsl[0] to ((hsl[1] - 0.22f) / 0.66f).coerceIn(0f, 1f)
}

private fun hexOf(color: Color) = "%06X".format(color.toArgb() and 0xFFFFFF)

@Composable
private fun HexColorDialog(initial: Color?, onDismiss: () -> Unit, onApply: (Color) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val start = initial ?: accentFrom(220f, 0.6f)
    var color by remember { mutableStateOf(start) }
    var hue by remember { mutableFloatStateOf(hueIntensityOf(start).first) }
    var intensity by remember { mutableFloatStateOf(hueIntensityOf(start).second) }
    var field by remember { mutableStateOf(TextFieldValue(hexOf(start))) }

    fun fromSliders(h: Float, i: Float) {
        hue = h
        intensity = i
        color = accentFrom(h, i)
        field = TextFieldValue(hexOf(color), selection = TextRange(6))
    }

    val hex = field.text
    val typedValid = hex.length == 6

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
                Text("Custom color", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                ColorPreview(color)

                Spacer(Modifier.height(22.dp))
                SliderLabel("Hue")
                GradientSlider(
                    value = hue / 360f,
                    brush = remember { Brush.horizontalGradient(List(13) { accentFrom(it * 30f, 1f) }) },
                    thumb = color,
                    label = "Hue",
                    onChange = { fromSliders(it * 360f, intensity) },
                )
                Spacer(Modifier.height(16.dp))
                SliderLabel("Intensity")
                GradientSlider(
                    value = intensity,
                    brush = remember(hue) { Brush.horizontalGradient(listOf(accentFrom(hue, 0f), accentFrom(hue, 1f))) },
                    thumb = color,
                    label = "Intensity",
                    onChange = { fromSliders(hue, it) },
                )

                Spacer(Modifier.height(20.dp))
                // Exact value: always in sync with the sliders; typing a code moves them.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("HEX", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text("#", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    Spacer(Modifier.width(2.dp))
                    BasicTextField(
                        value = field,
                        // Only drop invalid characters; never rewrite case while the keyboard is
                        // composing (that scrambles input). Shown upper-case.
                        onValueChange = { v ->
                            val clean = v.text.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                            field = if (clean == v.text && clean.length <= 6) {
                                v
                            } else {
                                val t = clean.take(6)
                                TextFieldValue(t, selection = TextRange(t.length))
                            }
                            if (field.text.length == 6) {
                                runCatching { Color(0xFF000000 or field.text.toLong(16)) }.getOrNull()?.let { c ->
                                    color = c
                                    val (h, i) = hueIntensityOf(c)
                                    hue = h
                                    intensity = i
                                }
                            }
                        },
                        visualTransformation = UppercaseTransformation,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                val extreme = color.luminance() > 0.7f || color.luminance() < 0.02f
                if (!typedValid || extreme) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (!typedValid) "Enter a 6-digit hex code." else "Very light or dark colors are softened so text stays readable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f), height = 44.dp)
                    PrimaryButton(
                        "Apply",
                        onClick = { if (typedValid) onApply(color) },
                        modifier = Modifier.weight(1f),
                        height = 44.dp,
                    )
                }
            }
        }
    }
}

/** What the accent will look like: a main button, an icon button and a selected tag. */
@Composable
private fun ColorPreview(color: Color) {
    val colors = MaterialTheme.colorScheme
    val onColor = if (color.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(40.dp)
                .clip(CircleShape)
                .background(color)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Extract", style = MaterialTheme.typography.labelLarge, color = onColor)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Download, contentDescription = null, tint = color, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.weight(1f))
        Text(
            "SPLIT",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SliderLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

/**
 * A pill-shaped gradient track with a round thumb filled with the current colour. Drawn in a single
 * Canvas (no child layouts), and only while the dialog is open.
 */
@Composable
private fun GradientSlider(value: Float, brush: Brush, thumb: Color, label: String, onChange: (Float) -> Unit) {
    val ring = Color.White
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                val inset = size.height / 2f
                fun emit(x: Float) = currentOnChange(((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    emit(down.position.x)
                    down.consume()
                    drag(down.id) { change ->
                        emit(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        val r = size.height / 2f
        drawRoundRect(brush, cornerRadius = CornerRadius(r, r))
        val cx = r + value.coerceIn(0f, 1f) * (size.width - 2 * r)
        val c = Offset(cx, r)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = r + 1.dp.toPx(), center = c + Offset(0f, 1.dp.toPx()))
        drawCircle(ring, radius = r, center = c)
        drawCircle(thumb, radius = r - 3.5.dp.toPx(), center = c)
    }
}
