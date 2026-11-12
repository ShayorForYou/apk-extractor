package com.shayor.apkextractor.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.selected
import com.shayor.apkextractor.data.SplitMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shayor.apkextractor.ui.theme.LocalCellColor
import com.shayor.apkextractor.ui.theme.iosColors
import kotlin.math.ceil

// -------------------------------------------------------------------------------------------------
// Switch
// -------------------------------------------------------------------------------------------------

/**
 * The iOS switch: a green capsule with a white knob that glides across on a spring and stretches
 * while pressed. With [onCheckedChange] null it is display-only (the whole row toggles instead).
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val p = iosColors
    val haptics = LocalHapticFeedback.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "switch",
    )
    val stretch by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 900f), label = "knob")
    val offTrack = if (p.dark) Color(0xFF39393D) else Color(0xFFE9E9EA)
    val on = p.green
    Box(
        modifier
            .size(51.dp, 31.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onCheckedChange(it)
                        },
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .drawBehind {
                val t = progress
                drawRoundRect(lerp(offTrack, on, t), cornerRadius = CornerRadius(size.height / 2))
                val pad = 2.dp.toPx()
                val d = size.height - 2 * pad
                val w = d + 7.dp.toPx() * stretch
                val x = pad + (size.width - 2 * pad - w) * t
                drawRoundRect(
                    Color.Black.copy(alpha = 0.10f),
                    topLeft = Offset(x, pad + 1.5.dp.toPx()),
                    size = Size(w, d),
                    cornerRadius = CornerRadius(d / 2),
                )
                drawRoundRect(Color.White, topLeft = Offset(x, pad), size = Size(w, d), cornerRadius = CornerRadius(d / 2))
            },
    )
}

// -------------------------------------------------------------------------------------------------
// Activity indicator
// -------------------------------------------------------------------------------------------------

/**
 * UIActivityIndicatorView: eight spokes with a bright head chasing round. While [spinning] is false
 * the spokes appear one by one with [revealed] (0..1), as when pulling to refresh.
 */
@Composable
fun ActivityIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = iosColors.secondaryLabel,
    spinning: Boolean = true,
    revealed: () -> Float = { 1f },
) {
    val head = if (spinning) {
        val transition = rememberInfiniteTransition(label = "spinner")
        transition.animateFloat(0f, 8f, infiniteRepeatable(tween(850, easing = LinearEasing)), label = "head")
    } else {
        null
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Loading" }) {
        val n = 8
        val shown = if (spinning) n else ceil(revealed().coerceIn(0f, 1f) * n).toInt()
        val r = this.size.minDimension / 2
        val stroke = r * 0.2f
        val step = head?.value?.toInt() ?: 0
        for (i in 0 until shown) {
            val age = ((step - i) % n + n) % n // 0 = brightest
            val a = if (head != null) 1f - age * 0.1f else 0.75f
            rotate(i * 360f / n) {
                drawLine(
                    color.copy(alpha = color.alpha * a),
                    start = Offset(center.x, center.y - r * 0.48f),
                    end = Offset(center.x, center.y - r + stroke / 2),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Inset grouped lists
// -------------------------------------------------------------------------------------------------

val GroupRadius = 24.dp
val GroupShape = RoundedCornerShape(GroupRadius)

/** Row background: white / #1C1C1E, or one step lighter inside a sheet. */
@Composable
fun cellColor(): Color = LocalCellColor.current.takeOrElse { iosColors.cell }

/** Shape of one row of a group drawn item by item (in a lazy list): round only the outer corners. */
fun groupItemShape(first: Boolean, last: Boolean, radius: Dp = GroupRadius): Shape = when {
    first && last -> RoundedCornerShape(radius)
    first -> RoundedCornerShape(topStart = radius, topEnd = radius)
    last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    else -> RoundedCornerShape(0.dp)
}

/** An inset grouped section: rounded rows on the grouped background. */
@Composable
fun InsetGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(cellColor()),
        content = content,
    )
}

/** Small uppercase caption above a group. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 24.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = iosColors.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Explanatory text under a group. */
@Composable
fun SectionFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = iosColors.secondaryLabel,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
    )
}

/** Hairline between rows, inset from the leading edge as on iOS. */
@Composable
fun CellDivider(start: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(iosColors.separator),
    )
}

/** iOS Settings-style icon: a white glyph on a small coloured rounded square. */
@Composable
fun IconTile(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = iosColors.tertiaryLabel,
        modifier = modifier.size(22.dp),
    )
}

/**
 * A row of an inset grouped list: optional leading icon, title (and subtitle), a value on the right
 * in secondary grey, then a chevron when it opens something.
 */
@Composable
fun Cell(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    chevron: Boolean = onClick != null,
    titleColor: Color = iosColors.label,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = iosColors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(start = 16.dp, end = if (chevron) 10.dp else 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(14.dp))
        }
        // With a value, the title keeps its natural width and the value takes the rest (right-aligned).
        Column(if (value == null) Modifier.weight(1f) else Modifier.widthIn(max = 220.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = p.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(2.dp))
            Chevron()
        }
    }
}

/** A row with an [IosSwitch]; the whole row toggles. */
@Composable
fun SwitchCell(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null, // the knob reacts, the row doesn't flash
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onCheckedChange(it)
                },
            )
            .padding(start = 16.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = iosColors.secondaryLabel)
            }
        }
        Spacer(Modifier.width(12.dp))
        IosSwitch(checked, onCheckedChange = null, interactionSource = source)
    }
}

// -------------------------------------------------------------------------------------------------
// Sheets
// -------------------------------------------------------------------------------------------------

/**
 * An iOS sheet: large rounded top corners, a grabber, the grouped background, and rows one step
 * lighter so groups stand out. Never taller than ~90% of the screen, so it reads as a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSheet(
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = iosColors
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.sheet,
        contentColor = p.label,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        scrimColor = Color.Black.copy(alpha = if (p.dark) 0.55f else 0.28f),
        dragHandle = { Grabber() },
    ) {
        CompositionLocalProvider(LocalCellColor provides p.elevatedCell) {
            Column(Modifier.heightIn(max = maxHeight), content = content)
        }
    }
}

@Composable
private fun Grabber() {
    Box(
        Modifier
            .padding(top = 6.dp, bottom = 4.dp)
            .size(36.dp, 5.dp)
            .clip(CircleShape)
            .background(iosColors.fill),
    )
}

/** Sheet title bar: centred title and a round close button, as in iOS sheets. */
@Composable
fun SheetHeader(title: String, onClose: (() -> Unit)?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
            maxLines = 1,
        )
        if (onClose != null) CloseButton(onClose, Modifier.align(Alignment.CenterEnd))
    }
}

/** The grey round "×" that closes iOS sheets and cards. */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, description: String = "Close") {
    val p = iosColors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(30.dp)
            .pressScale(source, 0.88f)
            .clip(CircleShape)
            .background(p.tertiaryFill)
            .clickable(source, indication = null, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Close, contentDescription = description, tint = p.secondaryLabel, modifier = Modifier.size(17.dp))
    }
}

// -------------------------------------------------------------------------------------------------
// Pull-down menus
// -------------------------------------------------------------------------------------------------

/**
 * An iOS pull-down menu: a rounded, softly raised panel with an optional small title, rows with a
 * leading checkmark column and a trailing symbol, and an optional footnote.
 */
@Composable
fun IosMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    width: Dp = 264.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = iosColors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = if (p.dark) Color(0xFF2C2C2E) else Color(0xFFF9F9FB),
        shadowElevation = 18.dp,
        border = BorderStroke(0.5.dp, p.separator),
        modifier = modifier.width(width),
    ) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryLabel,
                modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
            )
        }
        content()
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryLabel,
                modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * A row of an [IosMenu]. [checked] draws the leading checkmark (iOS marks the current choice that
 * way); [divider] draws the hairline above the row.
 */
@Composable
fun IosMenuItem(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null,
    checked: Boolean = false,
    checkColumn: Boolean = true,
    divider: Boolean = true,
    tint: Color = iosColors.label,
) {
    val p = iosColors
    if (divider) {
        Box(
            Modifier
                .padding(start = if (checkColumn) 46.dp else 16.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(p.separator),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (checkColumn) selected = checked }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checkColumn) {
            Box(Modifier.width(30.dp)) {
                if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = p.label, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.secondaryLabel)
        }
        if (icon != null) {
            Spacer(Modifier.width(10.dp))
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The share button. For split apps it opens a menu to choose how they are shared: one merged APK,
 * or the original parts as an .apks bundle. Plain APKs are shared straight away.
 */
@Composable
fun ShareButton(
    hasSplit: Boolean,
    onShare: (SplitMode?) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    description: String = "Share",
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TonalIconButton(
            Icons.Rounded.Share,
            description,
            onClick = { if (hasSplit) open = true else onShare(null) },
            size = size,
        )
        IosMenu(open, onDismiss = { open = false }, title = "Share Split Apps As") {
            IosMenuItem(
                "Single APK",
                onClick = {
                    open = false
                    onShare(SplitMode.MERGE)
                },
                icon = Icons.Rounded.Android,
                subtitle = "One file that installs anywhere",
                checkColumn = false,
                divider = false,
            )
            IosMenuItem(
                "APKS Bundle",
                onClick = {
                    open = false
                    onShare(SplitMode.BUNDLE)
                },
                icon = Icons.Rounded.Layers,
                subtitle = "Original parts and signature",
                checkColumn = false,
            )
        }
    }
}
