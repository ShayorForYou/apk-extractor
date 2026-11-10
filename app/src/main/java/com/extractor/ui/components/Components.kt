package com.extractor.ui.components

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extractor.data.AppIcons
import com.extractor.data.AppInfo
import com.extractor.data.ThemeMode

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
 * A small, tracked uppercase tag (APK, SPLIT, APKS…). Neutral for plain APKs, softly tinted with the
 * accent for split apps and bundles, so the two read apart at a glance without shouting.
 */
@Composable
fun FormatTag(format: PackageFormat, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = MaterialTheme.colorScheme
    val tint = if (format.accent) colors.primary else colors.onSurfaceVariant
    Text(
        if (detail != null) "${format.tag}  ·  $detail" else format.tag,
        style = MaterialTheme.typography.labelSmall.merge(TagStyle),
        color = tint,
        maxLines = 1,
        modifier = modifier
            .semantics { contentDescription = format.description }
            .clip(TagShape)
            .background(tint.copy(alpha = if (format.accent) 0.12f else 0.09f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

private val TagShape = RoundedCornerShape(6.dp)
private val TagStyle = TextStyle(
    fontSize = 10.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.8.sp,
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
                    .clip(RoundedCornerShape(percent = 28))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Surfaces
// -------------------------------------------------------------------------------------------------

/** Card with a hairline border — the basic building block of every screen. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    border: Color? = MaterialTheme.colorScheme.outlineVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val stroke = border?.let { BorderStroke(1.dp, it) }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = stroke, modifier = modifier, content = content)
    } else {
        Surface(shape = shape, color = color, border = stroke, modifier = modifier, content = content)
    }
}

@Composable
fun PanelColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Panel(modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 4.dp), content = content) }
}

/** Round icon button: soft-filled, or white with a hairline border. */
@Composable
fun SquareButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        contentColor = colors.onSurface,
        border = if (filled) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

// -------------------------------------------------------------------------------------------------
// Buttons — one family: soft rounded rectangles that dip slightly when pressed.
// -------------------------------------------------------------------------------------------------

/** Shrinks slightly while pressed, for a tactile feel. Animates scale only (cheap, no relayout). */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource, pressed: Float = 0.97f): Modifier {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Main action: a solid accent pill. The icon sits in its own soft circle at the leading edge,
 * which gives the button a clear focal point without gradients or shadows.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(height)
            .pressScale(source)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(
                interactionSource = source,
                indication = ripple(color = colors.onPrimary),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (icon != null) 8.dp else 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val badge = height - 16.dp
        if (icon != null) {
            // Icon anchored at the leading edge in its own soft circle…
            Box(
                Modifier
                    .size(badge)
                    .clip(CircleShape)
                    .background(colors.onPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(20.dp)) }
        }
        // …label centred in the remaining space, balanced by an equal trailing gap.
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.1.sp),
            color = colors.onPrimary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = if (icon != null) Modifier.weight(1f).padding(horizontal = 8.dp) else Modifier,
        )
        if (icon != null) Spacer(Modifier.width(badge))
    }
}

/** Quiet action (Cancel / Close): a soft tonal pill in the accent family. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .height(height)
            .pressScale(source)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(
                interactionSource = source,
                indication = ripple(color = colors.primary),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer, maxLines = 1)
    }
}

/** Round tonal icon button (download, share, open folder…), with the same press feedback. */
@Composable
fun TonalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(source, pressed = 0.9f)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(
                interactionSource = source,
                indication = ripple(color = colors.primary),
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.primary, modifier = Modifier.size(size * 0.48f))
    }
}

/** Square shortcut tile: icon in a tinted circle, label underneath. */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.4f
    Column(
        modifier
            .pressScale(source)
            .clip(RoundedCornerShape(20.dp))
            // White on light sheets; one step brighter than the sheet in dark mode so tiles read.
            .background(if (colors.background.luminance() < 0.5f) colors.surfaceContainerHigh else colors.surfaceContainerLowest)
            .clickable(
                interactionSource = source,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer { this.alpha = alpha }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface, maxLines = 1)
    }
}

@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

// -------------------------------------------------------------------------------------------------
// Typography helpers
// -------------------------------------------------------------------------------------------------

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
        color = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}

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
            .clip(RoundedCornerShape(size * 0.3f))
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

/** Section title with an optional one-line subtitle and trailing actions. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actions != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Tabs & segmented control
// -------------------------------------------------------------------------------------------------

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    content: @Composable (option: T, active: Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            segment * index,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "thumb",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(colors.surfaceBright),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEach { option ->
                val active = option == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(option) }),
                    contentAlignment = Alignment.Center,
                ) { content(option, active) }
            }
        }
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
    ThemeMode.SYSTEM -> "Auto"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
fun ThemeSelector(mode: ThemeMode, onChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SegmentedControl(themeModes, mode, onChange, modifier) { m, active ->
        val tint by animateColorAsState(if (active) colors.onSurface else colors.onSurfaceVariant, label = "seg")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(m.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(m.label(), style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Rows, empty & loading states
// -------------------------------------------------------------------------------------------------

@Composable
fun IconTile(icon: ImageVector, accent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (accent) colors.primary else colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) colors.onPrimary else colors.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            Row(content = action)
        }
    }
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    Panel(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(block))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(CircleShape).background(block))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.7f).height(10.dp).clip(CircleShape).background(block))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(CircleShape).background(block))
            }
        }
    }
}
