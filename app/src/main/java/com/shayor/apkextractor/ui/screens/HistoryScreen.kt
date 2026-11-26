package com.shayor.apkextractor.ui.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Android
import com.shayor.apkextractor.ui.components.SectionLabelStyle
import com.shayor.apkextractor.ui.components.groupEdge
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
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
import kotlinx.coroutines.launch
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
    /** The search text, typed in the dock beside the tab bar. */
    query: String,
    onQueryChange: (String) -> Unit,
    contentPadding: PaddingValues,
    onBrowseApps: () -> Unit,
    onInstall: (HistoryEntry) -> Unit,
) {
    val entries by vm.history.collectAsStateWithLifecycle()
    // A search keeps the files whose app, package, version or file name match.
    val term = query.trim()
    val searching = term.isNotEmpty()
    val shown = remember(entries, term) {
        if (term.isEmpty()) entries
        else entries.filter { e ->
            e.label.contains(term, ignoreCase = true) ||
                e.packageName.contains(term, ignoreCase = true) ||
                e.versionName.contains(term, ignoreCase = true) ||
                e.fileName.contains(term, ignoreCase = true)
        }
    }
    // Entries are newest first, and groupBy keeps first-seen order, so groups are too.
    val groups = remember(shown) { shown.groupBy { it.packageName }.values.toList() }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledUnderBar()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val missingIds by vm.missingIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Install only after the file is confirmed to be there (it may have gone since the last check).
    fun install(entry: HistoryEntry) {
        scope.launch { if (vm.ensureReadable(entry)) onInstall(entry) }
    }

    val items = remember(groups, expanded, searching) {
        buildList {
            groups.forEach { group ->
                val latest = group.first()
                val pkg = latest.packageName
                val older = group.size - 1
                // While searching, every match is in view: each app's card is open.
                val open = searching || pkg in expanded
                add(HistoryItem.Latest(latest, first = true, last = older == 0))
                if (older > 0) {
                    add(HistoryItem.Toggle(pkg, older, open, last = !open))
                    if (open) group.drop(1).forEachIndexed { i, e -> add(HistoryItem.Version(e, last = i == older - 1)) }
                }
            }
        }
    }

    // The list ends above the keyboard while the search field is up.
    val density = LocalDensity.current
    val keyboardLift = with(density) {
        (WindowInsets.ime.getBottom(this) - WindowInsets.navigationBars.getBottom(this)).coerceAtLeast(0).toDp()
    }
    val titleHighlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarHeight() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + keyboardLift,
            ),
            modifier = Modifier
                .fillMaxSize()
                .glassSource(),
        ) {
            item(key = "summary") {
                Text(
                    when {
                        searching -> if (shown.size == 1) "1 RESULT" else "${shown.size} RESULTS"
                        entries.isEmpty() -> ""
                        entries.size == 1 -> "1 SAVED FILE"
                        groups.size == entries.size -> "${entries.size} SAVED FILES"
                        groups.size == 1 -> "${entries.size} SAVED VERSIONS OF ${groups[0].first().label.uppercase()}"
                        else -> "${entries.size} SAVED FILES FROM ${groups.size} APPS"
                    },
                    style = SectionLabelStyle,
                    color = iosColors.tertiaryLabel,
                    modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 8.dp),
                )
            }
            val gone = entries.count { it.id in missingIds }
            if (gone > 0 && !searching) {
                item(key = "missing") { MissingBanner(gone, onRemove = vm::removeMissing) }
            }
            if (searching && shown.isEmpty()) {
                item(key = "noResults") {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No Results",
                        message = "No saved file matches “$term”",
                    ) { PrimaryButton("Clear Search", onClick = { onQueryChange("") }, height = 44.dp) }
                }
            }
            if (shown.isNotEmpty()) {
                itemsIndexed(items, key = { _, it -> it.key }) { i, item ->
                    val shape = groupItemShape(item.first, item.last)
                    when (item) {
                        is HistoryItem.Latest -> SwipeToRemove(
                            onRemove = { vm.deleteSaved(item.entry) },
                            shape = shape,
                            modifier = Modifier
                                .animateItem()
                                .padding(top = if (i > 0) 14.dp else 0.dp)
                                .groupEdge(item.first, item.last, iosColors.separator),
                        ) {
                            HistoryRow(
                                entry = item.entry,
                                title = remember(item.entry.label, term, titleHighlight) {
                                    highlight(item.entry.label, term, titleHighlight)
                                },
                                missing = item.entry.id in missingIds,
                                onInstall = { install(item.entry) },
                                onShare = { vm.shareHistory(item.entry) },
                            )
                        }

                        is HistoryItem.Toggle -> VersionsToggle(
                            count = item.count,
                            open = item.open,
                            shape = shape,
                            onClick = { expanded = if (item.open) expanded - item.pkg else expanded + item.pkg },
                            modifier = Modifier.animateItem().groupEdge(item.first, item.last, iosColors.separator),
                        )

                        is HistoryItem.Version -> SwipeToRemove(
                            onRemove = { vm.deleteSaved(item.entry) },
                            shape = shape,
                            modifier = Modifier.animateItem().groupEdge(item.first, item.last, iosColors.separator),
                        ) {
                            VersionRow(
                                entry = item.entry,
                                missing = item.entry.id in missingIds,
                                onInstall = { install(item.entry) },
                                onShare = { vm.shareHistory(item.entry) },
                            )
                        }
                    }
                }
                item(key = "hint") {
                    Text(
                        "Swipe a file to delete it.",
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
                val settings by vm.settings.collectAsStateWithLifecycle()
                HistoryEmpty(
                    location = settings.outputFolderName ?: com.shayor.apkextractor.data.ApkExporter.DEFAULT_LOCATION,
                    onBrowseApps = onBrowseApps,
                )
            }
        }

        GlassTopBar(
            "History",
            collapsed = collapsed,
            actions = if (entries.isNotEmpty()) {
                {
                    // Every file is saved to one folder: the newest file's is where new ones go too.
                    TonalIconButton(
                        Icons.Rounded.FolderOpen,
                        "Open folder",
                        onClick = { vm.openFolder(entries.first().treeUri) },
                        size = 32.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    ClearAllButton(onClick = { confirmClear = true })
                }
            } else {
                null
            },
        )
    }

    if (confirmClear && entries.isNotEmpty()) {
        val locations = remember(entries) { entries.map { it.location }.distinct() }
        ClearHistorySheet(
            count = entries.size,
            bytes = remember(entries) { entries.sumOf { it.sizeBytes.coerceAtLeast(0) } },
            location = locations.singleOrNull() ?: "${locations.size} folders",
            onDelete = { vm.clearHistory(deleteFiles = true) },
            onKeepFiles = { vm.clearHistory(deleteFiles = false) },
            onDismiss = { confirmClear = false },
        )
    }
}

/**
 * Clear All, as a soft red pill the height of the folder button beside it: it reads as the
 * destructive one of the pair without shouting (the sheet it opens asks before anything goes).
 */
@Composable
private fun ClearAllButton(onClick: () -> Unit) {
    val p = iosColors
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(32.dp)
            .pressScale(source, 0.92f)
            .clip(CircleShape)
            .background(p.red.copy(alpha = if (p.dark) 0.16f else 0.10f))
            .border(1.dp, p.red.copy(alpha = if (p.dark) 0.28f else 0.22f), CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = p.red, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("Clear All", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = p.red)
    }
}

private val AutoTag = PackageFormat("AUTO", "Saved by auto-backup", accent = false)

/** Hairline at the top of a row that isn't the first of its card. */
private fun Modifier.topDivider(color: Color, inset: Dp) = drawBehind {
    val x = inset.toPx()
    drawRect(color, topLeft = Offset(x, 0f), size = Size(size.width - x, 0.5.dp.toPx()))
}

/** An app's newest saved file, at the top of its card. Same actions as its older versions. */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    /** The app name, with a search match marked. */
    title: AnnotatedString,
    missing: Boolean,
    onInstall: () -> Unit,
    onShare: () -> Unit,
) {
    val p = iosColors
    Row(
        Modifier
            .fillMaxWidth()
            .background(cellColor())
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.packageName, "${entry.packageName}@history", 44.dp, backupUri = entry.uri)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            VersionLine(entry, MaterialTheme.typography.bodySmall, p.secondaryLabel)
            Spacer(Modifier.height(3.dp))
            if (missing) MissingLine() else MetaLine(entry)
        }
        if (!missing) RowActions(entry, onInstall = onInstall, onShare = onShare)
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
private fun VersionRow(entry: HistoryEntry, missing: Boolean, onInstall: () -> Unit, onShare: () -> Unit) {
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
            VersionLine(entry, MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), p.label)
            if (missing) MissingLine() else MetaLine(entry)
        }
        if (!missing) RowActions(entry, onInstall = onInstall, onShare = onShare)
    }
}

/** The share and install buttons of a row whose file is there. */
@Composable
private fun RowActions(entry: HistoryEntry, onInstall: () -> Unit, onShare: () -> Unit) {
    Spacer(Modifier.width(8.dp))
    TonalIconButton(Icons.Rounded.Share, "Share ${entry.fileName}", onClick = onShare, size = 30.dp)
    Spacer(Modifier.width(8.dp))
    PillButton("Install", onClick = onInstall)
}

/** Replaces a row's details when its file is gone. Only Delete (swipe) and Remove Missing apply. */
@Composable
private fun MissingLine() {
    Text(
        "File missing from its folder",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Above the list when saved files are gone: says so, and takes them off History in one step. */
@Composable
private fun MissingBanner(count: Int, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(groupItemShape(true, true))
                .background(cellColor())
                .groupEdge(true, true, iosColors.separator)
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (count == 1) "1 saved file is missing" else "$count saved files are missing",
                style = MaterialTheme.typography.bodyMedium,
                color = iosColors.label,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Remove",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onRemove)
                    .padding(start = 12.dp),
            )
        }
    }
}

/** "v1.5.0 · 20 MB": the version in [style], the size always plain and grey. */
@Composable
private fun VersionLine(entry: HistoryEntry, style: TextStyle, color: Color) {
    val size = rememberFileSize(entry.sizeBytes)
    val muted = iosColors.secondaryLabel
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append(versionLabel(entry.versionName))
            if (entry.sizeBytes > 0) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = muted, fontWeight = FontWeight.Normal)) {
                    append("  ·  $size")
                }
            }
        },
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** When it was saved, and what kind of file (APK / APKS, AUTO for auto-backups). */
@Composable
private fun MetaLine(entry: HistoryEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            relativeTime(entry.time),
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


// -------------------------------------------------------------------------------------------------
// Empty history
// -------------------------------------------------------------------------------------------------

/**
 * Nothing saved yet: a small fan of file cards over a soft accent glow (the side cards open out
 * once, on a spring, when the screen first shows; nothing loops), a title, what History is for,
 * the way to start, and where files will be saved.
 */
@Composable
private fun HistoryEmpty(location: String, onBrowseApps: () -> Unit) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 260f))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val glow = accent.copy(alpha = if (p.dark) 0.20f else 0.12f)
        Box(
            Modifier
                .size(width = 200.dp, height = 150.dp)
                .drawBehind {
                    drawCircle(
                        androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow, Color.Transparent), center = center, radius = size.width * 0.55f),
                        radius = size.width * 0.55f,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            FileCard(Modifier.graphicsLayer {
                val t = reveal.value
                rotationZ = -12f * t
                translationX = -30.dp.toPx() * t
                translationY = 6.dp.toPx() * t
                alpha = 0.55f * t.coerceIn(0f, 1f)
            }, accent, faded = true)
            FileCard(Modifier.graphicsLayer {
                val t = reveal.value
                rotationZ = 10f * t
                translationX = 30.dp.toPx() * t
                translationY = 4.dp.toPx() * t
                alpha = 0.75f * t.coerceIn(0f, 1f)
            }, accent, faded = true)
            FileCard(Modifier.graphicsLayer {
                val t = reveal.value
                val scale = 0.92f + 0.08f * t
                scaleX = scale
                scaleY = scale
                translationY = -4.dp.toPx() * t
            }, accent, faded = false)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Nothing saved yet",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Every app you extract is kept here, ready to share, move or reinstall.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.secondaryLabel,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Browse Apps", onClick = onBrowseApps, height = 46.dp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = p.tertiaryLabel, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Saving to $location",
                style = MaterialTheme.typography.labelMedium,
                color = p.tertiaryLabel,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** One card of the fan: an app-file tile and two lines of "text". */
@Composable
private fun FileCard(modifier: Modifier, accent: Color, faded: Boolean) {
    val p = iosColors
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .size(width = 92.dp, height = 112.dp)
            .clip(shape)
            .background(if (faded) p.tertiaryFill else cellColor())
            .border(1.dp, p.separator, shape)
            .padding(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accent.copy(alpha = if (faded) 0.10f else if (p.dark) 0.20f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!faded) Icon(Icons.Rounded.Android, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(54.dp).height(7.dp).clip(CircleShape).background(p.separator))
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(34.dp).height(7.dp).clip(CircleShape).background(p.separator))
    }
}
