package com.shayor.apkextractor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.data.HistoryEntry
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.EmptyState
import com.shayor.apkextractor.ui.components.FormatTag
import com.shayor.apkextractor.ui.components.GlassTopBar
import com.shayor.apkextractor.ui.components.PackageFormat
import com.shayor.apkextractor.ui.components.PillButton
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.cellColor
import com.shayor.apkextractor.ui.components.glassSource
import com.shayor.apkextractor.ui.components.groupItemShape
import com.shayor.apkextractor.ui.components.packageFormat
import com.shayor.apkextractor.ui.components.pressScale
import com.shayor.apkextractor.ui.components.relativeTime
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.scrolledUnderBar
import com.shayor.apkextractor.ui.components.topBarHeight
import com.shayor.apkextractor.ui.components.versionLabel
import com.shayor.apkextractor.ui.theme.iosColors

/** Where row text starts (16 + 44dp icon + 14), so dividers and older versions line up under it. */
private val TextInset = 74.dp

/** One line of the history list: an app's card is its newest file, a toggle, and its older versions. */
private sealed interface HistoryItem {
    val key: String
    val first: Boolean
    val last: Boolean

    class Latest(val entry: HistoryEntry, override val first: Boolean, override val last: Boolean) : HistoryItem {
        // Keyed by the entry, not the app: rows remember their swipe state per key, and a per-app
        // key would hand a just-swiped-away state to the next version (or to an undo).
        override val key = "app:${entry.id}"
    }

    class Toggle(val pkg: String, val count: Int, val open: Boolean, override val last: Boolean) : HistoryItem {
        override val key = "toggle:$pkg"
        override val first = false
    }

    class Version(val entry: HistoryEntry, override val last: Boolean) : HistoryItem {
        override val key = "v:${entry.id}"
        override val first = false
    }
}

/**
 * Saved files, one card per app (its newest file). Apps saved more than once — by hand or by
 * auto-backup — list their older versions inside the card, each of which can be installed again.
 */
@Composable
fun HistoryScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onBrowseApps: () -> Unit,
    onInstall: (HistoryEntry) -> Unit,
) {
    val entries by vm.history.collectAsStateWithLifecycle()
    // Entries are newest first, and groupBy keeps first-seen order, so groups are too.
    val groups = remember(entries) { entries.groupBy { it.packageName }.values.toList() }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledUnderBar()

    val items = remember(groups, expanded) {
        buildList {
            groups.forEach { group ->
                val latest = group.first()
                val pkg = latest.packageName
                val older = group.size - 1
                val open = pkg in expanded
                add(HistoryItem.Latest(latest, first = true, last = older == 0))
                if (older > 0) {
                    add(HistoryItem.Toggle(pkg, older, open, last = !open))
                    if (open) group.drop(1).forEachIndexed { i, e -> add(HistoryItem.Version(e, last = i == older - 1)) }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarHeight() + 4.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            modifier = Modifier
                .fillMaxSize()
                .glassSource(),
        ) {
            item(key = "summary") {
                Text(
                    when {
                        entries.isEmpty() -> ""
                        entries.size == 1 -> "1 SAVED FILE"
                        groups.size == entries.size -> "${entries.size} SAVED FILES"
                        groups.size == 1 -> "${entries.size} SAVED VERSIONS OF ${groups[0].first().label.uppercase()}"
                        else -> "${entries.size} SAVED FILES FROM ${groups.size} APPS"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = iosColors.secondaryLabel,
                    modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 7.dp),
                )
            }
            if (entries.isNotEmpty()) {
                itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                    val shape = groupItemShape(item.first, item.last)
                    when (item) {
                        is HistoryItem.Latest -> SwipeToRemove(
                            onRemove = { vm.removeHistory(item.entry) },
                            shape = shape,
                            modifier = Modifier
                                .animateItem()
                                .padding(top = if (i > 0) 14.dp else 0.dp),
                        ) {
                            HistoryRow(
                                entry = item.entry,
                                onOpen = { vm.openHistoryFolder(item.entry) },
                                onShare = { vm.shareHistory(item.entry) },
                            )
                        }

                        is HistoryItem.Toggle -> VersionsToggle(
                            count = item.count,
                            open = item.open,
                            shape = shape,
                            onClick = { expanded = if (item.open) expanded - item.pkg else expanded + item.pkg },
                            modifier = Modifier.animateItem(),
                        )

                        is HistoryItem.Version -> SwipeToRemove(
                            onRemove = { vm.removeHistory(item.entry) },
                            shape = shape,
                            modifier = Modifier.animateItem(),
                        ) {
                            VersionRow(
                                entry = item.entry,
                                onInstall = { onInstall(item.entry) },
                                onShare = { vm.shareHistory(item.entry) },
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Swipe a file to remove it from the list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = iosColors.secondaryLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            // Centred in the space between the top bar and the tab bar.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight(), bottom = contentPadding.calculateBottomPadding()),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "No Saved Apps",
                    message = "Apps you extract will show up here.",
                ) {
                    PrimaryButton("Browse Apps", onClick = onBrowseApps, height = 44.dp)
                }
            }
        }

        GlassTopBar(
            "History",
            collapsed = collapsed,
            actions = if (entries.isNotEmpty()) {
                { BarTextButton("Clear All", onClick = vm::clearHistory) }
            } else {
                null
            },
        )
    }
}

/** A plain accent-coloured word in the navigation bar, like iOS bar button items. */
@Composable
private fun BarTextButton(text: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

private val AutoTag = PackageFormat("AUTO", "Saved by auto-backup", accent = false)

/** Hairline at the top of a row that isn't the first of its card. */
private fun Modifier.topDivider(color: Color, inset: Dp) = drawBehind {
    val x = inset.toPx()
    drawRect(color, topLeft = Offset(x, 0f), size = Size(size.width - x, 0.5.dp.toPx()))
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onOpen: () -> Unit, onShare: () -> Unit) {
    val p = iosColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(cellColor())
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.packageName, "${entry.packageName}@history", 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            MetaLine(entry)
        }
        Spacer(Modifier.width(8.dp))
        TonalIconButton(Icons.Rounded.Share, "Share ${entry.fileName}", onClick = onShare, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        TonalIconButton(Icons.Rounded.FolderOpen, "Open folder", onClick = onOpen, size = 32.dp)
    }
}

@Composable
private fun VersionsToggle(count: Int, open: Boolean, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = iosColors
    val rotation by animateFloatAsState(if (open) 90f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "chevron")
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cellColor())
            .topDivider(p.separator, TextInset)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = TextInset, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count == 1) "1 Older Version" else "$count Older Versions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (open) "Hide older versions" else "Show older versions",
            tint = p.tertiaryLabel,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/** An older saved version of an app, inside its app's card. */
@Composable
private fun VersionRow(entry: HistoryEntry, onInstall: () -> Unit, onShare: () -> Unit) {
    val p = iosColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(cellColor())
            .topDivider(p.separator, TextInset)
            .padding(start = TextInset, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                versionLabel(entry.versionName).take(24),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(entry)
        }
        Spacer(Modifier.width(8.dp))
        TonalIconButton(Icons.Rounded.Share, "Share ${entry.fileName}", onClick = onShare, size = 30.dp)
        Spacer(Modifier.width(8.dp))
        PillButton("Install", onClick = onInstall)
    }
}

@Composable
private fun MetaLine(entry: HistoryEntry) {
    val size = rememberFileSize(entry.sizeBytes)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val parts = buildList {
            add(relativeTime(entry.time))
            if (entry.sizeBytes > 0) add(size)
        }
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = iosColors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        packageFormat(entry.fileName)?.let {
            Spacer(Modifier.width(6.dp))
            FormatTag(it)
        }
        if (entry.auto) {
            Spacer(Modifier.width(4.dp))
            FormatTag(AutoTag)
        }
    }
}

/** iOS swipe-to-delete: the row slides away over a red "Delete" action. */
@Composable
private fun SwipeToRemove(
    onRemove: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = iosColors
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) onRemove()
    }
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier.clip(shape),
        backgroundContent = {
            val fromStart = dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier
                    .fillMaxSize()
                    .background(p.red)
                    .padding(horizontal = 22.dp),
                horizontalArrangement = if (fromStart) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete", style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        },
    ) { content() }
}
