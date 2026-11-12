package com.shayor.apkextractor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import com.shayor.apkextractor.ui.theme.LocalIosPalette
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------------------------------
// Press feedback
// -------------------------------------------------------------------------------------------------

/**
 * iOS-style press feedback for rows and plain buttons: a soft grey wash that appears the moment the
 * finger lands and fades out slowly after it lifts. Provided as the app's default indication.
 */
object IosHighlight : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlightNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 7
}

private class HighlightNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    private val amount = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var pressed = 0
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed++
                    is PressInteraction.Release, is PressInteraction.Cancel -> pressed = (pressed - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                launch {
                    if (pressed > 0) amount.animateTo(1f, tween(70)) else amount.animateTo(0f, tween(320))
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val a = amount.value
        if (a > 0f) drawRect(currentValueOf(LocalIosPalette).label.copy(alpha = 0.09f * a))
    }
}

/**
 * Sinks slightly while pressed and springs back on release, like iOS buttons and cards.
 * Scale only (draw phase), so it costs nothing to lay out.
 */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        if (isPressed) spring(dampingRatio = 1f, stiffness = 1400f) else spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
