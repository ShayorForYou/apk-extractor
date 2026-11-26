package com.shayor.apkextractor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shayor.apkextractor.ui.theme.iosColors
import kotlinx.coroutines.flow.first
import kotlin.math.hypot

/** Gap between the tab bar and the round search button beside it. */
private val DockGap = 10.dp

/**
 * The bottom dock: the tab bar with a round search button beside it. Tapping the button sends a
 * wave out of it that opens the search field over the tab bar, while the tab bar shrinks away and
 * the button turns into Cancel. Cancel runs it all backwards.
 *
 * One value (0 = tabs, 1 = search) drives everything, and it is only read in the draw phase, so
 * the dock doesn't recompose while it animates.
 */
@Composable
fun SearchDock(
    /** False hides the search button (tabs other than Apps); the tab bar takes the full width. */
    searchAvailable: Boolean,
    open: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    tabBar: @Composable (Modifier) -> Unit,
) {
    val reveal = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) {
        reveal.animateTo(
            if (open) 1f else 0f,
            if (open) spring(dampingRatio = 0.9f, stiffness = 170f) else spring(dampingRatio = 1f, stiffness = 380f),
        )
    }
    // Compose each side only while it can be seen.
    val showTabs by remember { derivedStateOf { reveal.value < 0.999f } }
    val showField by remember { derivedStateOf { reveal.value > 0.001f } }

    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // The keyboard waits until the wave has mostly crossed, so the wave plays where it started
    // before the keyboard lifts the field.
    LaunchedEffect(open) {
        if (open) {
            snapshotFlow { reveal.value }.first { it > 0.75f }
            runCatching { focus.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(Chrome.TabBarHeight)) {
            if (showTabs) {
                // The wave washes over the tab bar: everything inside its edge is cut away, while
                // the rest sinks back a little, so it is gone just as the wave reaches its far end.
                val hole = remember { Path() }
                tabBar(
                    Modifier
                        .graphicsLayer {
                            val t = reveal.value
                            val s = 1f - 0.06f * t
                            scaleX = s
                            scaleY = s
                            alpha = (1f - (t - 0.55f) / 0.45f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .drawWithContent {
                            val t = reveal.value
                            if (t <= 0.001f) {
                                drawContent()
                            } else {
                                hole.reset()
                                hole.addOval(Rect(waveCenter(size), waveRadius(size, t)))
                                clipPath(hole, ClipOp.Difference) { this@drawWithContent.drawContent() }
                            }
                        },
                )
            }
            if (showField) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Fully open: no clip, so the glass keeps its whole shadow.
                            if (reveal.value < 0.999f) {
                                clip = true
                                shape = CircleReveal(waveCenter(size), waveRadius(size, reveal.value))
                            } else {
                                clip = false
                                shape = RectangleShape
                            }
                        },
                ) {
                    SearchCapsule(query, onQueryChange, placeholder, focus)
                    // The crest of the wave: thin accent rings riding its edge, fading as it lands.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .drawBehind {
                                val t = reveal.value
                                if (t >= 0.999f) return@drawBehind
                                val c = waveCenter(size)
                                val r = waveRadius(size, t)
                                val fade = 1f - t
                                drawCircle(accent.copy(alpha = 0.55f * fade), r - 1.5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                                drawCircle(accent.copy(alpha = 0.22f * fade), r * 0.82f, c, style = Stroke(1.5.dp.toPx()))
                                drawCircle(accent.copy(alpha = 0.10f * fade), r * 0.64f, c, style = Stroke(1.dp.toPx()))
                            },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = searchAvailable,
            enter = fadeIn(tween(180)) + expandHorizontally(expandFrom = Alignment.Start) + scaleIn(initialScale = 0.6f),
            exit = fadeOut(tween(120)) + shrinkHorizontally(shrinkTowards = Alignment.Start) + scaleOut(targetScale = 0.6f),
        ) {
            Row {
                Spacer(Modifier.width(DockGap))
                SearchButton(open, { reveal.value }, onClick = { if (open) onCancel() else onOpen() }, tint = p.label, accent = accent)
            }
        }
    }
}

/** The wave's centre: the middle of the search button, just right of the field's box. */
private fun Density.waveCenter(size: Size) =
    Offset(size.width + (DockGap + Chrome.TabBarHeight / 2).toPx(), size.height / 2)

/** The wave's radius at [t]: from the button's own edge out to the field's far corners. */
private fun Density.waveRadius(size: Size, t: Float): Float {
    val c = waveCenter(size)
    val start = (Chrome.TabBarHeight / 2).toPx()
    return start + (hypot(c.x, c.y) - start) * t
}

/** The round glass button: a magnifier that turns into a cancel cross as search opens. */
@Composable
private fun SearchButton(open: Boolean, progress: () -> Float, onClick: () -> Unit, tint: Color, accent: Color) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(Chrome.TabBarHeight)
            .pressScale(source, 0.88f)
            .glass(CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (open) "Cancel search" else "Search apps" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    val t = progress()
                    alpha = (1f - t * 1.6f).coerceIn(0f, 1f)
                    rotationZ = -90f * t
                    scaleX = 1f - 0.4f * t
                    scaleY = 1f - 0.4f * t
                },
        )
        Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    val t = progress()
                    alpha = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                    rotationZ = 90f * (1f - t)
                    scaleX = 0.6f + 0.4f * t
                    scaleY = 0.6f + 0.4f * t
                },
        )
    }
}

/** The search field itself: a glass capsule the height of the tab bar. */
@Composable
private fun SearchCapsule(query: String, onQueryChange: (String) -> Unit, placeholder: String, focus: FocusRequester) {
    val p = iosColors
    val focusManager = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxSize()
            .glass(CircleShape)
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = p.secondaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.label),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = "Clear search",
                tint = p.tertiaryLabel,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") }
                    .padding(6.dp),
            )
        }
    }
}

/** A circle of [radius] around [center]: the edge of the opening wave. */
private class CircleReveal(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(center, radius)) })
}
