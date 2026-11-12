package com.shayor.apkextractor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.shayor.apkextractor.ui.theme.iosColors
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/*
 * Glass: bars and floating controls blur whatever scrolls beneath them, like iOS materials.
 *
 * Each page's scrolling content is a glass *source* ([glassSource]); bars and floating capsules
 * drawn above it are glass *effects* ([glass], [GlassTopBar]). On Android 12+ the backdrop is
 * really blurred (RenderEffect); older versions get an almost opaque tint instead.
 */

/** The app-wide backdrop that glass surfaces blur. Null in previews / isolated sheets. */
val LocalGlassState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Identifies the page a glass source / effect belongs to. Glass inside a page blurs only that
 * page's content, so a swipe between tabs doesn't make every bar draw every page.
 */
val LocalGlassKey = staticCompositionLocalOf<Any?> { null }

/** Layout of the app chrome, shared by the bars and the pages that scroll beneath them. */
object Chrome {
    /** Large-title row of a top bar (expanded) and its inline row (collapsed). */
    val LargeTitleRow = 48.dp
    val InlineTitleRow = 44.dp

    /** The floating tab bar, and the gap between it and the bottom edge / gesture bar. */
    val TabBarHeight = 64.dp
    val TabBarGap = 8.dp

    /** Side margin of the floating capsules. */
    val FloatingMargin = 16.dp
}

/** Distance from the bottom of the window to the top of the floating tab bar. */
@Composable
fun tabBarClearance(): Dp = with(LocalDensity.current) {
    WindowInsets.navigationBars.getBottom(this).toDp()
} + Chrome.TabBarGap + Chrome.TabBarHeight

/** Height of an expanded [GlassTopBar], status bar included; [extra] is its `bottom` slot. */
@Composable
fun topBarHeight(extra: Dp = 0.dp): Dp = with(LocalDensity.current) {
    WindowInsets.statusBars.getTop(this).toDp()
} + Chrome.LargeTitleRow + extra

/**
 * Marks scrolling content as the backdrop for glass. Paints the page background into it, so the
 * blur always has something opaque to work with.
 */
@Composable
fun Modifier.glassSource(): Modifier {
    val state = LocalGlassState.current
    val key = LocalGlassKey.current
    val bg = MaterialTheme.colorScheme.background
    return (if (state != null) hazeSource(state, key = key) else this).background(bg)
}

@Composable
private fun glassStyle(tint: Color): HazeStyle {
    val p = iosColors
    return remember(p, tint) {
        HazeStyle(
            backgroundColor = p.groupedBackground,
            tints = listOf(HazeTint(tint)),
            blurRadius = 26.dp,
            noiseFactor = 0f,
            // No blur before Android 12: a near-opaque fill still reads as the same material.
            fallbackTint = HazeTint(tint.copy(alpha = 0.95f)),
        )
    }
}

/**
 * A floating piece of glass (tab bar, search field, toolbars, toasts): a soft drop shadow, the
 * blurred backdrop under a light tint, and a thin rim that catches the light along the top edge.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun Modifier.glass(shape: Shape, tint: Color = iosColors.glassTint, shadow: Boolean = true): Modifier {
    val p = iosColors
    val state = LocalGlassState.current
    val key = LocalGlassKey.current
    val style = glassStyle(tint)
    val rim = remember(p) { Brush.verticalGradient(listOf(p.glassRimTop, p.glassRimBottom)) }
    return this
        .then(
            if (shadow) {
                Modifier.dropShadow(
                    shape,
                    Shadow(
                        radius = 22.dp,
                        color = Color.Black,
                        offset = DpOffset(0.dp, 6.dp),
                        alpha = if (p.dark) 0.45f else 0.13f,
                    ),
                )
            } else {
                Modifier
            },
        )
        .clip(shape)
        .then(
            if (state != null) {
                Modifier.hazeEffect(state, style) {
                    inputScale = HazeInputScale.Fixed(0.4f)
                    if (key != null) canDrawArea = { it.key == key }
                }
            } else {
                Modifier.background(tint.copy(alpha = 0.97f))
            },
        )
        .border(0.8.dp, rim, shape)
}

// -------------------------------------------------------------------------------------------------
// Top bar
// -------------------------------------------------------------------------------------------------

/** True once [this] list has scrolled its content under the top bar. */
@Composable
fun LazyListState.scrolledUnderBar(): State<Boolean> {
    val threshold = with(LocalDensity.current) { 6.dp.toPx() }
    return remember(this, threshold) {
        derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > threshold }
    }
}

@Composable
fun ScrollState.scrolledUnderBar(): State<Boolean> {
    val threshold = with(LocalDensity.current) { 6.dp.toPx() }
    return remember(this, threshold) { derivedStateOf { value > threshold } }
}

/**
 * iOS navigation bar. At rest it is transparent and shows a left-aligned large title; once content
 * scrolls under it, the large title gives way to a centred inline title and the bar frosts over
 * (glass + hairline). Everything animates in the draw / layout phases only.
 *
 * Place it above content whose top padding is [topBarHeight] (plus the height of [bottom]).
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassTopBar(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    val p = iosColors
    val state = LocalGlassState.current
    val key = LocalGlassKey.current
    val style = glassStyle(p.glassTint)
    val fraction = animateFloatAsState(
        if (collapsed) 1f else 0f,
        spring(dampingRatio = 1f, stiffness = 380f),
        label = "barCollapse",
    )
    // No blur at all while nothing is under the bar (the usual resting state at the top).
    val frosted by remember { derivedStateOf { fraction.value > 0.01f } }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (state != null && frosted) {
                    Modifier.hazeEffect(state, style) {
                        alpha = fraction.value
                        inputScale = HazeInputScale.Fixed(0.4f)
                        if (key != null) canDrawArea = { it.key == key }
                    }
                } else {
                    Modifier.drawBehind { drawRect(p.groupedBackground.copy(alpha = fraction.value)) }
                },
            )
            .statusBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val h = lerp(Chrome.LargeTitleRow, Chrome.InlineTitleRow, fraction.value).roundToPx()
                    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                    layout(placeable.width, h) { placeable.place(0, 0) }
                },
        ) {
            // Large title: slides up a touch and fades as the bar collapses.
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium, // 28sp: iOS Title 1, a touch under Large Title
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp, end = if (actions != null) 132.dp else 20.dp)
                    .semantics { heading() }
                    .graphicsLayer {
                        val f = fraction.value
                        alpha = (1f - f * 1.8f).coerceIn(0f, 1f)
                        translationY = -10.dp.toPx() * f
                        val s = 1f - 0.08f * f
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
            // Inline title: fades in once the large one is mostly gone.
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        val f = fraction.value
                        alpha = ((f - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        translationY = 6.dp.toPx() * (1f - f)
                    },
            )
            if (actions != null) {
                Row(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
        bottom?.invoke()
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = fraction.value }
                .background(p.separator),
        )
    }
}
