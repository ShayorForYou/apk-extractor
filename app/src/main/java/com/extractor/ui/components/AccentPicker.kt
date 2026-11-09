package com.extractor.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.extractor.ui.theme.AccentPresets

/**
 * Accent swatches plus a "custom" swatch that accepts any hex colour.
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

@Composable
private fun HexColorDialog(initial: Color?, onDismiss: () -> Unit, onApply: (Color) -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Opens with the current code fully selected, so typing a new one simply replaces it.
    var field by remember {
        val start = initial?.let { "%06X".format(it.toArgb() and 0xFFFFFF) } ?: ""
        mutableStateOf(TextFieldValue(start, selection = TextRange(0, start.length)))
    }
    val hex = field.text
    val parsed = if (hex.length == 6) runCatching { Color(0xFF000000 or hex.toLong(16)) }.getOrNull() else null

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Custom color", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(parsed ?: colors.surfaceContainerHigh)
                            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(14.dp))
                    Surface(
                        shape = CircleShape,
                        color = colors.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (hex.isEmpty()) {
                                    Text("3E7CB1", style = MaterialTheme.typography.titleMedium, color = colors.outline)
                                }
                                BasicTextField(
                                    value = field,
                                    // Only drop invalid characters; never rewrite case while the
                                    // keyboard is composing (that scrambles input). Shown upper-case.
                                    onValueChange = { v ->
                                        val clean = v.text.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                                        field = if (clean == v.text && clean.length <= 6) {
                                            v
                                        } else {
                                            val t = clean.take(6)
                                            TextFieldValue(t, selection = TextRange(t.length))
                                        }
                                    },
                                    visualTransformation = UppercaseTransformation,
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
                                    cursorBrush = SolidColor(colors.primary),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        keyboardType = KeyboardType.Ascii,
                                        imeAction = ImeAction.Done,
                                        autoCorrectEnabled = false,
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        parsed == null -> "Enter a 6-digit hex code."
                        parsed.luminance() > 0.7f || parsed.luminance() < 0.02f ->
                            "Very light or dark colours are softened so text stays readable."
                        else -> "Looks good."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onApply) }, enabled = parsed != null) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
