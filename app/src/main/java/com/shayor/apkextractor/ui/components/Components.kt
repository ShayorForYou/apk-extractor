package com.shayor.apkextractor.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import com.shayor.apkextractor.ui.theme.JakartaSans
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shayor.apkextractor.data.AppIcons
import com.shayor.apkextractor.data.AppInfo
import com.shayor.apkextractor.data.ThemeMode
import com.shayor.apkextractor.ui.theme.iosColors

// -------------------------------------------------------------------------------------------------
// Basics
// -------------------------------------------------------------------------------------------------

@Composable
fun rememberFileSize(bytes: Long): String {
    val context = LocalContext.current
    return remember(bytes) { Formatter.formatShortFileSize(context, bytes) }
}

fun versionLabel(app: AppInfo): String = versionLabel(app.displayVersion)

fun versionLabel(version: String): String =
    if (version.startsWith("v", ignoreCase = true)) version else "v$version"

// -------------------------------------------------------------------------------------------------
// Format tags
// -------------------------------------------------------------------------------------------------

/** What a package or file is, as shown by [FormatTag]. [accent] marks the multi-file formats. */
@Immutable
data class PackageFormat(val tag: String, val description: String, val accent: Boolean)

/** An installed app: one APK, or a base APK plus split APKs. */
fun packageFormat(app: AppInfo): PackageFormat {
    val parts = app.splitSourceDirs.size + 1
    return if (app.isSplit) PackageFormat("SPLIT", "Split APK, $parts parts", accent = true)
    else PackageFormat("APK", "Single APK", accent = false)
}

/** A saved file, by extension; null when it isn't an app package. */
fun packageFormat(fileName: String): PackageFormat? =
    when (val ext = fileName.substringAfterLast('.', "").lowercase()) {
        "apk" -> PackageFormat("APK", "APK file", accent = false)
        "apks", "xapk", "apkm" -> PackageFormat(ext.uppercase(), "${ext.uppercase()} bundle", accent = true)
        else -> null
    }

/**
 * A small uppercase capsule (APK, SPLIT, APKS…), like the badges in iOS lists. Grey for plain APKs,
 * accent-tinted for split apps and bundles.
 */
@Composable
fun FormatTag(format: PackageFormat, modifier: Modifier = Modifier, detail: String? = null) {
    val tint = if (format.accent) MaterialTheme.colorScheme.primary else iosColors.secondaryLabel
    Text(
        if (detail != null) "${format.tag}  ·  $detail" else format.tag,
        style = TagStyle,
        color = tint,
        maxLines = 1,
        modifier = modifier
            .semantics { contentDescription = format.description }
            .clip(CircleShape)
            .background(if (format.accent) tint.copy(alpha = 0.13f) else iosColors.tertiaryFill)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

private val TagStyle = TextStyle(
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.5.sp,
)

fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    if (now - time < DateUtils.MINUTE_IN_MILLIS) return "Just now"
    return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

/**
 * App icon loaded asynchronously at the exact pixel size it is drawn at. For a saved file, pass its
 * [backupUri]: the icon then still shows after the app is uninstalled.
 */
@Composable
fun AppIcon(
    packageName: String,
    iconKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
    backupUri: String? = null,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(AppIcons.cached(iconKey, px), iconKey, px) {
        if (value == null) value = AppIcons.load(context, packageName, iconKey, px, backupUri)
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize(0.9f)
                    .clip(RoundedCornerShape(percent = 26))
                    .background(iosColors.tertiaryFill),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Surfaces
// -------------------------------------------------------------------------------------------------

/** A rounded card in the row colour: the building block of inset grouped screens. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = GroupShape,
    color: Color = cellColor(),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, iosColors.separator, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

// -------------------------------------------------------------------------------------------------
// Buttons — iOS capsules: filled (accent), tinted (accent on a soft tint) and round icon buttons.
// Pressed, they sink a little and dim, then spring back.
// -------------------------------------------------------------------------------------------------

@Composable
private fun Modifier.dimWhilePressed(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        if (pressed) 0.72f else 1f,
        if (pressed) tween(60) else tween(260),
        label = "dim",
    )
    return graphicsLayer { this.alpha = alpha }
}

/** Main action: a filled accent capsule. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 50.dp,
    /** Fill; e.g. red for a destructive action. The label is then white. */
    color: Color = Color.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val fill = color.takeOrElse { colors.primary }
    val content = if (color.isSpecified) Color.White else colors.onPrimary
    Row(
        modifier
            .height(height)
            .pressScale(source)
            .dimWhilePressed(source)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
    }
}

/** Quiet action (Cancel / Close): accent text on a soft accent tint, same size as [PrimaryButton]. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 50.dp,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(height)
            .pressScale(source)
            .dimWhilePressed(source)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = if (iosColors.dark) 0.16f else 0.10f))
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = colors.primary, maxLines = 1)
    }
}

/** Round icon button (download, share, open folder…): accent glyph on a soft grey fill. */
@Composable
fun TonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(source, pressed = 0.86f)
            .dimWhilePressed(source)
            .clip(CircleShape)
            .background(iosColors.tertiaryFill)
            .border(1.dp, iosColors.separator, CircleShape)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.primary, modifier = Modifier.size(size * 0.52f))
    }
}

/** App Store–style "GET" pill: bold accent label on a grey capsule. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(32.dp)
            .pressScale(source, pressed = 0.9f)
            .dimWhilePressed(source)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = if (iosColors.dark) 0.16f else 0.10f))
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (icon != null && text.isEmpty()) 13.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(4.dp))
        }
        if (text.isNotEmpty()) {
            Text(text, style = PillStyle, color = colors.primary, maxLines = 1)
        }
    }
}

private val PillStyle = TextStyle(fontFamily = JakartaSans, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)

// -------------------------------------------------------------------------------------------------
// Toasts — glass capsules
// -------------------------------------------------------------------------------------------------

/** Snackbar content for an undoable action; [durationMs] drives the draining progress line. */
class UndoVisuals(
    override val message: String,
    val durationMs: Long,
    val tone: ToastTone = ToastTone.Removed,
) : SnackbarVisuals {
    override val actionLabel: String = "Undo"
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite // timed by the caller
}

/**
 * What a toast is telling you, shown by its leading glyph. [Deleted] is for files gone from disk
 * (muted red trash); [Removed] is for entries taken off a list with nothing deleted (gray).
 */
enum class ToastTone { Info, Success, Warning, Deleted, Removed }

/** A plain message with a [tone]. */
class ToastVisuals(override val message: String, val tone: ToastTone) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** Shows [text] as a toast with a [tone] glyph. */
suspend fun SnackbarHostState.toast(text: String, tone: ToastTone = ToastTone.Info) =
    showSnackbar(ToastVisuals(text, tone))

/**
 * A glass pill that hugs its message: a tone glyph, the text, and for an undoable change an
 * "Undo" button ringed by the time left. Rises in on a spring, fades out, and can be swiped
 * aside. Only the ring and the swipe animate after it lands (draw and layout phases).
 */
@Composable
private fun GlassToast(data: SnackbarData) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val undo = data.visuals as? UndoVisuals
    val tone = undo?.tone ?: (data.visuals as? ToastVisuals)?.tone ?: ToastTone.Info
    val remaining = remember(data) { Animatable(1f) }
    if (undo != null) {
        LaunchedEffect(data) { remaining.animateTo(0f, tween(undo.durationMs.toInt(), easing = LinearEasing)) }
    }
    val (glyph, toneColor) = when (tone) {
        ToastTone.Success -> Icons.Rounded.Check to p.green
        ToastTone.Warning -> Icons.Rounded.PriorityHigh to p.orange
        ToastTone.Deleted -> Icons.Rounded.Delete to p.red
        ToastTone.Removed -> Icons.Rounded.Delete to p.gray
        ToastTone.Info -> Icons.Rounded.Info to accent
    }
    // Over a sheet the toast would share the sheet's colour: lift it a step so it reads on top.
    val onSheet = LocalToastOnSheet.current
    val base = if (onSheet) lerp(p.sheet, if (p.dark) Color.White else Color.Black, if (p.dark) 0.10f else 0.05f) else p.glassTint
    val wash = remember(toneColor, p.dark) {
        Brush.horizontalGradient(0f to toneColor.copy(alpha = if (p.dark) 0.20f else 0.12f), 0.65f to Color.Transparent)
    }
    val quiet = tone == ToastTone.Deleted || tone == ToastTone.Removed
    val rim = when {
        quiet -> if (p.dark) 0.22f else 0.18f
        else -> if (p.dark) 0.38f else 0.30f
    }
    val swipe = remember(data) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // A capsule at one line; a softly rounded card if the message wraps.
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .padding(horizontal = Chrome.FloatingMargin)
            .widthIn(max = 440.dp)
            .offset { IntOffset(swipe.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(swipe.value) / (size.width.coerceAtLeast(1f) * 0.8f)).coerceIn(0f, 1f) }
            .draggable(
                rememberDraggableState { delta -> scope.launch { swipe.snapTo(swipe.value + delta) } },
                Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val far = abs(swipe.value) > with(density) { 96.dp.toPx() } || abs(velocity) > 1800f
                    if (far) {
                        val direction = if (swipe.value + velocity * 0.05f >= 0f) 1f else -1f
                        swipe.animateTo(direction * with(density) { 480.dp.toPx() }, tween(160))
                        data.dismiss()
                    } else {
                        swipe.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f))
                    }
                },
            )
            .semantics { liveRegion = LiveRegionMode.Polite }
            .glass(shape, tint = base)
            // A wash of the tone's colour from the glyph's side, and a rim of it round the edge.
            .background(wash)
            // Softer for removals, so a delete reads as "gone, undo if you like", not as an error.
            .border(1.dp, toneColor.copy(alpha = rim), shape),
    ) {
        Row(
            Modifier
                .heightIn(min = 52.dp)
                .padding(start = 10.dp, end = if (undo != null) 8.dp else 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = toneColor
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = if (p.dark) 0.20f else 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(glyph, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (undo != null) {
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(role = Role.Button, onClick = data::performAction)
                        .padding(start = 6.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The arrow inside a ring that drains over the time left to undo.
                    Box(
                        Modifier
                            .size(26.dp)
                            .drawBehind {
                                val stroke = 2.dp.toPx()
                                val arc = Size(size.width - stroke, size.height - stroke)
                                val topLeft = Offset(stroke / 2, stroke / 2)
                                drawArc(accent.copy(alpha = 0.22f), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                                drawArc(accent, -90f, 360f * remaining.value, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp)) }
                    Spacer(Modifier.width(6.dp))
                    Text(undo.actionLabel, style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }
        }
    }
}

/**
 * Where toasts are shown. The app's own host draws them over the screens; a sheet (its own window,
 * above the app) registers a layer while it's open, and the topmost layer draws them instead, so a
 * toast is never hidden behind a sheet and never shows twice.
 */
object ToastCenter {
    internal var state by mutableStateOf<SnackbarHostState?>(null)
    internal var scope: CoroutineScope? = null
    internal val layers = mutableStateListOf<Any>()

    /** True while a sheet's layer is showing the toasts instead of the app's host. */
    val coveredBySheet: Boolean get() = layers.isNotEmpty()

    /** Shows [text] from anywhere (no coroutine needed), replacing the current toast. */
    fun show(text: String, tone: ToastTone = ToastTone.Info): Boolean {
        val host = state ?: return false
        val launcher = scope ?: return false
        launcher.launch {
            host.currentSnackbarData?.dismiss()
            host.toast(text, tone)
        }
        return true
    }
}

/** Makes [state] the app's toast host for [ToastCenter.show] and sheet layers. */
@Composable
fun ProvideToasts(state: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    DisposableEffect(state, scope) {
        ToastCenter.state = state
        ToastCenter.scope = scope
        onDispose {
            if (ToastCenter.state === state) {
                ToastCenter.state = null
                ToastCenter.scope = null
            }
        }
    }
}

/**
 * Called inside a sheet: while it is the top sheet, shows the toasts in a small floating layer on
 * its window, at the bottom of the screen. The layer wraps the toast, so the rest of the sheet
 * stays touchable.
 */
@Composable
fun ToastLayer() {
    val state = ToastCenter.state ?: return
    val token = remember { Any() }
    DisposableEffect(Unit) {
        ToastCenter.layers += token
        onDispose { ToastCenter.layers -= token }
    }
    if (ToastCenter.layers.lastOrNull() !== token) return
    val density = LocalDensity.current
    val bottom = WindowInsets.navigationBars.getBottom(density) + with(density) { 12.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(bottom) { BottomCenterPosition(bottom) },
        properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        // The blurred backdrop belongs to the app's window, not this one: use the solid glass fill.
        CompositionLocalProvider(LocalGlassState provides null, LocalToastOnSheet provides true) {
            AppSnackbarHost(state, Modifier.padding(top = 16.dp))
        }
    }
}

/** True for toasts drawn in a sheet's layer. */
private val LocalToastOnSheet = staticCompositionLocalOf { false }

private class BottomCenterPosition(private val bottom: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset((windowSize.width - popupContentSize.width) / 2, windowSize.height - popupContentSize.height - bottom)
}

/**
 * Shows the current toast centred above whatever sits below it. A replacement crossfades in place.
 * Plain toasts time out like Material's short snackbar (longer with accessibility services on);
 * undoable ones are timed by their caller.
 */
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val data = state.currentSnackbarData
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(data) {
        if (data == null) return@LaunchedEffect
        val base = when (data.visuals.duration) {
            SnackbarDuration.Short -> 4000L
            SnackbarDuration.Long -> 10000L
            SnackbarDuration.Indefinite -> return@LaunchedEffect
        }
        delay(
            accessibility?.calculateRecommendedTimeoutMillis(
                base, containsIcons = true, containsText = true, containsControls = data.visuals.actionLabel != null,
            ) ?: base,
        )
        data.dismiss()
    }
    AnimatedContent(
        targetState = data,
        transitionSpec = {
            (
                slideInVertically(spring(dampingRatio = 0.72f, stiffness = 520f)) { it } +
                    scaleIn(spring(dampingRatio = 0.72f, stiffness = 520f), initialScale = 0.88f) +
                    fadeIn(tween(160))
                ) togetherWith (fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.94f)) using
                SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomCenter,
        label = "toast",
        modifier = modifier,
    ) { current ->
        if (current != null) GlassToast(current) else Box(Modifier)
    }
}

// -------------------------------------------------------------------------------------------------
// Logo
// -------------------------------------------------------------------------------------------------

/**
 * The app's logo — the launcher icon's tile-out-of-tray glyph on its periwinkle gradient.
 * Drawn directly on a Canvas (same geometry as the icon's 108-unit vector) instead of
 * inflating the XML vector: no resource parsing on the first frame, and it is immune to
 * resource/code optimisation differences in release builds.
 */
@Composable
fun AppLogo(size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(LogoGradient),
    ) {
        // Map the icon's 108×108 design space so its glyph fills the tile like the launcher icon.
        val k = this.size.minDimension * 1.55f / 108f
        withTransform({
            translate(center.x - 54f * k, center.y - 54f * k)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 2f, center = Offset(73.5f, 29f))
            drawCircle(Color.White.copy(alpha = 0.5f), radius = 1.5f, center = Offset(35f, 33f))
            drawPath(TrayPath, Color.White, style = Stroke(width = 6.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            rotate(-10f, pivot = Offset(54f, 40f)) {
                drawRoundRect(Color.White, topLeft = Offset(42f, 28f), size = Size(24f, 24f), cornerRadius = CornerRadius(7f))
                drawPath(ArrowPath, LogoGlyph, style = Stroke(width = 3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

private val LogoGradient = Brush.linearGradient(listOf(Color(0xFF8B96FF), Color(0xFF4A54CC)))
private val LogoGlyph = Color(0xFF5560DA)
private val TrayPath = PathParser().parsePathString("M33,57v7a10,10 0,0 0,10 10h22a10,10 0,0 0,10 -10v-7").toPath()
private val ArrowPath = PathParser().parsePathString("M54,46V35.5M49,40.5l5,-5l5,5").toPath()

// -------------------------------------------------------------------------------------------------
// Segmented control
// -------------------------------------------------------------------------------------------------

/**
 * UISegmentedControl: a grey capsule track with a raised white thumb that glides (with a little
 * spring) to the chosen segment.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    content: @Composable (option: T, active: Boolean) -> Unit,
) {
    val p = iosColors
    val index = options.indexOf(selected).coerceAtLeast(0)
    val thumbColor = if (p.dark) Color(0xFF2A2F3A) else Color.White
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(if (p.dark) Color(0xFF1A1E26) else Color(0xFFECEEF3))
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            segment * index,
            spring(dampingRatio = 0.75f, stiffness = 450f),
            label = "thumb",
        )
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .dropShadow(CircleShape, Shadow(radius = 4.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.12f))
                .clip(CircleShape)
                .background(thumbColor),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            interactionSource = null,
                            indication = null,
                            onClick = { onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) { content(option, active) }
            }
        }
    }
}

/** Plain text segment label: semibold when chosen, as on iOS. */
@Composable
fun SegmentLabel(text: String, active: Boolean, icon: ImageVector? = null) {
    val p = iosColors
    val tint by animateColorAsState(if (active) p.label else p.tertiaryLabel, label = "seg")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = tint,
            maxLines = 1,
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Theme
// -------------------------------------------------------------------------------------------------

private val themeModes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)

fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Rounded.LightMode
    ThemeMode.DARK -> Icons.Rounded.DarkMode
}

fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Automatic"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
fun ThemeSelector(mode: ThemeMode, onChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    SegmentedControl(themeModes, mode, onChange, modifier) { m, active -> SegmentLabel(m.label(), active, m.icon()) }
}

// -------------------------------------------------------------------------------------------------
// Empty & loading states
// -------------------------------------------------------------------------------------------------

/** ContentUnavailableView: a large grey symbol, a bold title, a line of help and an optional button. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val p = iosColors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = p.secondaryLabel, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = p.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Row(content = action)
        }
    }
}

/** A placeholder list row that breathes while the list loads. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(0.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    val block = iosColors.tertiaryFill
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cellColor())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .graphicsLayer { this.alpha = alpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(block))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(CircleShape).background(block))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(CircleShape).background(block))
        }
    }
}
