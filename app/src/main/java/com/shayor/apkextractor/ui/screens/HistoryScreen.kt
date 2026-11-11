package com.shayor.apkextractor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.data.HistoryEntry
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.EmptyState
import com.shayor.apkextractor.ui.components.FormatTag
import com.shayor.apkextractor.ui.components.PackageFormat
import com.shayor.apkextractor.ui.components.Panel
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.ScreenHeader
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.packageFormat
import com.shayor.apkextractor.ui.components.relativeTime
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.versionLabel

/**
 * Saved files, one card per app (its newest file). Apps saved more than once — by hand or by
 * auto-backup — list their older versions underneath, each of which can be installed again.
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

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            ScreenHeader(
                title = "History",
                subtitle = when {
                    entries.isEmpty() -> "Files you extract appear here"
                    entries.size == 1 -> "1 saved file"
                    groups.size == entries.size -> "${entries.size} saved files"
                    groups.size == 1 -> "${entries.size} saved versions of ${groups[0].first().label}"
                    else -> "${entries.size} saved files from ${groups.size} apps"
                },
            ) {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = vm::clearHistory) {
                        Text("Clear all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Nothing here yet",
                    message = "Apps you extract will show up here.",
                ) {
                    PrimaryButton(
                        "Browse apps",
                        onClick = onBrowseApps,
                        height = 44.dp,
                    )
                }
            }
        } else {
            groups.forEach { group ->
                val latest = group.first()
                val pkg = latest.packageName
                val open = pkg in expanded
                // Keyed by the entry, not the app: rows remember their swipe state per key, and a
                // per-app key would hand a just-swiped-away state to the next version (or to an undo).
                item(key = "app:${latest.id}") {
                    HistoryRow(
                        entry = latest,
                        olderCount = group.size - 1,
                        expanded = open,
                        onToggleVersions = { expanded = if (open) expanded - pkg else expanded + pkg },
                        onOpen = { vm.openHistoryFolder(latest) },
                        onShare = { vm.shareHistory(latest) },
                        onRemove = { vm.removeHistory(latest) },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (open) {
                    items(group.drop(1), key = { it.id }) { entry ->
                        VersionRow(
                            entry = entry,
                            onInstall = { onInstall(entry) },
                            onShare = { vm.shareHistory(entry) },
                            onRemove = { vm.removeHistory(entry) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Swipe an item to remove it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val AutoTag = PackageFormat("AUTO", "Saved by auto-backup", accent = false)

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    olderCount: Int,
    expanded: Boolean,
    onToggleVersions: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SwipeToRemove(onRemove, modifier) {
        Panel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = null, onClick = onOpen) {
            Column {
                Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(entry.packageName, "${entry.packageName}@history", 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        MetaLine(entry, showVersion = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    TonalIconButton(Icons.Rounded.Share, "Share ${entry.fileName}", onClick = onShare, size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                    TonalIconButton(Icons.Rounded.FolderOpen, "Open folder", onClick = onOpen, size = 36.dp)
                }
                if (olderCount > 0) {
                    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClick = onToggleVersions)
                            .background(colors.surfaceContainerHigh.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (olderCount == 1) "1 older version" else "$olderCount older versions",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Hide older versions" else "Show older versions",
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
                        )
                    }
                }
            }
        }
    }
}

/** An older saved version of an app, indented under its app's card. */
@Composable
private fun VersionRow(
    entry: HistoryEntry,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SwipeToRemove(onRemove, modifier.padding(start = 28.dp)) {
        Panel(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceContainerLow,
            border = colors.outlineVariant,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(colors.outline))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        versionLabel(entry.versionName).take(24),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    MetaLine(entry, showVersion = false)
                }
                Spacer(Modifier.width(8.dp))
                TonalIconButton(Icons.Rounded.Share, "Share ${entry.fileName}", onClick = onShare, size = 34.dp)
                Spacer(Modifier.width(6.dp))
                TonalIconButton(Icons.Rounded.InstallMobile, "Install ${versionLabel(entry.versionName)}", onClick = onInstall, size = 34.dp)
            }
        }
    }
}

@Composable
private fun MetaLine(entry: HistoryEntry, showVersion: Boolean) {
    val colors = MaterialTheme.colorScheme
    val size = rememberFileSize(entry.sizeBytes)
    Row(verticalAlignment = Alignment.CenterVertically) {
        val parts = buildList {
            if (showVersion) add(versionLabel(entry.versionName).take(16))
            add(relativeTime(entry.time))
            if (entry.sizeBytes > 0) add(size)
        }
        Text(
            parts.joinToString("  •  "),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        packageFormat(entry.fileName)?.let {
            Spacer(Modifier.width(8.dp))
            FormatTag(it)
        }
        if (entry.auto) {
            Spacer(Modifier.width(6.dp))
            FormatTag(AutoTag)
        }
    }
}

@Composable
private fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) onRemove()
    }
    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) { Icon(Icons.Rounded.Delete, contentDescription = null, tint = colors.onErrorContainer) }
        },
    ) { content() }
}
