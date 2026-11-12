package com.shayor.apkextractor.ui.components

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

/** App icon loaded asynchronously at the exact pixel size it is drawn at. */
@Composable
fun AppIcon(
    packageName: String,
    iconKey: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(AppIcons.cached(iconKey, px), iconKey, px) {
        if (value == null) value = AppIcons.load(context, packageName, iconKey, px)
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
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(height)
            .pressScale(source)
            .dimWhilePressed(source)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = colors.onPrimary, maxLines = 1)
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
            .background(colors.primary.copy(alpha = if (iosColors.dark) 0.20f else 0.12f))
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
            .height(30.dp)
            .pressScale(source, pressed = 0.9f)
            .dimWhilePressed(source)
            .clip(CircleShape)
            .background(iosColors.tertiaryFill)
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

private val PillStyle = TextStyle(fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)

// -------------------------------------------------------------------------------------------------
// Toasts — glass capsules
// -------------------------------------------------------------------------------------------------

/** Snackbar content for an undoable action; [durationMs] drives the draining progress line. */
class UndoVisuals(override val message: String, val durationMs: Long) : SnackbarVisuals {
    override val actionLabel: String = "Undo"
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite // timed by the caller
}

/**
 * A glass capsule: the message, an optional action, and (when undoable) a thin line along the
 * bottom that drains over the time left. Only the line animates (draw phase).
 */
@Composable
private fun GlassToast(data: SnackbarData, undo: UndoVisuals?) {
    val accent = MaterialTheme.colorScheme.primary
    val remaining = remember(data) { Animatable(1f) }
    if (undo != null) {
        LaunchedEffect(data) { remaining.animateTo(0f, tween(undo.durationMs.toInt(), easing = LinearEasing)) }
    }
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .padding(horizontal = Chrome.FloatingMargin)
            .fillMaxWidth()
            .glass(shape),
    ) {
        Row(
            Modifier
                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            val action = data.visuals.actionLabel
            if (action != null) {
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .height(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .clickable(role = Role.Button, onClick = data::performAction)
                        .padding(start = 12.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (undo != null) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(action, style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }
        }
        if (undo != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .drawBehind {
                        drawRoundRect(
                            accent,
                            size = Size(size.width * remaining.value, size.height),
                            cornerRadius = CornerRadius(size.height),
                        )
                    },
            )
        }
    }
}

@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> GlassToast(data, data.visuals as? UndoVisuals) }
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
    height: Dp = 34.dp,
    content: @Composable (option: T, active: Boolean) -> Unit,
) {
    val p = iosColors
    val index = options.indexOf(selected).coerceAtLeast(0)
    val thumbColor = if (p.dark) Color(0xFF636366) else Color.White
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(p.tertiaryFill)
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            segment * index,
            spring(dampingRatio = 0.78f, stiffness = 520f),
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
    val tint by animateColorAsState(if (active) p.label else p.label.copy(alpha = 0.8f), label = "seg")
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
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
