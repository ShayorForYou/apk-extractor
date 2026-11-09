package com.extractor.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.extractor.data.HistoryEntry
import com.extractor.ui.MainViewModel
import com.extractor.ui.components.AppIcon
import com.extractor.ui.components.EmptyState
import com.extractor.ui.components.ScreenHeader
import com.extractor.ui.components.Panel
import com.extractor.ui.components.PrimaryButton
import com.extractor.ui.components.TonalIconButton
import com.extractor.ui.components.relativeTime
import com.extractor.ui.components.rememberFileSize

@Composable
fun HistoryScreen(vm: MainViewModel, contentPadding: PaddingValues, onBrowseApps: () -> Unit) {
    val entries by vm.history.collectAsStateWithLifecycle()

    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "title") {
            ScreenHeader(
                title = "History",
                subtitle = if (entries.isEmpty()) "Files you extract appear here" else if (entries.size == 1) "1 saved file" else "${entries.size} saved files",
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
                        height = 46.dp,
                    )
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                HistoryRow(
                    entry = entry,
                    onOpen = { vm.openHistoryFolder(entry) },
                    onShare = { vm.shareHistory(entry) },
                    onRemove = { vm.removeHistory(entry) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "hint") {
                Text(
                    "Swipe an item to remove it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
    ) {
        Panel(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = null, onClick = onOpen) {
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
                    val size = rememberFileSize(entry.sizeBytes)
                    Text(
                        if (entry.sizeBytes > 0) "${relativeTime(entry.time)}  •  $size" else relativeTime(entry.time),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TonalIconButton(Icons.Rounded.Share, "Share ${entry.fileName}", onClick = onShare, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                TonalIconButton(Icons.Rounded.FolderOpen, "Open folder", onClick = onOpen, size = 40.dp)
            }
        }
    }
}
