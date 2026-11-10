package com.shayor.apkextractor.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.data.AppFilter
import com.shayor.apkextractor.data.AppInfo
import com.shayor.apkextractor.data.SortBy
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.EmptyState
import com.shayor.apkextractor.ui.components.SegmentedControl
import com.shayor.apkextractor.ui.components.Panel
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.SkeletonRow
import com.shayor.apkextractor.ui.components.SquareButton
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.versionLabel
import com.shayor.apkextractor.ui.components.FormatTag
import com.shayor.apkextractor.ui.components.packageFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onExtract: (Collection<String>) -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val state by vm.listState.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) { vm.setQuery(query) }
    val inSelection = selection.isNotEmpty()

    // Back order: keyboard first (keeps the search text), then selection, then the search itself.
    val imeVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }
    BackHandler(enabled = !imeVisible && !inSelection && query.isNotEmpty()) { query = "" }
    BackHandler(enabled = !imeVisible && inSelection) { vm.clearSelection() }
    BackHandler(enabled = imeVisible) {
        keyboard?.hide()
        focusManager.clearFocus()
    }

    // Keep the search field in view when the filter / sort / query changes.
    val listKey = "${settings.filter}|${settings.sortBy}|${settings.sortAscending}|$query"
    var lastListKey by rememberSaveable { mutableStateOf(listKey) }
    var scrollPending by remember { mutableStateOf(false) }
    LaunchedEffect(listKey) {
        if (listKey != lastListKey) {
            lastListKey = listKey
            scrollPending = true
            delay(400)
            if (scrollPending && listState.firstVisibleItemIndex > 2) listState.scrollToItem(2)
            scrollPending = false
        }
    }
    LaunchedEffect(state.apps) {
        if (scrollPending) {
            if (listState.firstVisibleItemIndex > 2) listState.scrollToItem(2)
            scrollPending = false
        }
    }

    // The floating dock sits just above the docked tab bar, and above the keyboard while typing.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val tabBarPx = with(density) { 77.dp.roundToPx() }
    val keyboardLift = with(density) { (imeBottom - navBottom - tabBarPx).coerceAtLeast(0).toDp() }

    // Scroll-to-top: only once the user is about a screen down, and only while they are scrolling
    // back up (or have stopped) — never in the way while they scroll down through the list.
    val scope = rememberCoroutineScope()
    var scrollingUp by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index != lastIndex || offset != lastOffset) {
                    scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset)
                }
                lastIndex = index
                lastOffset = offset
            }
    }
    val farDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 8 } }
    val showScrollTop = farDown && !inSelection && !imeVisible &&
        (scrollingUp || !listState.isScrollInProgress)

    Box(Modifier.fillMaxSize()) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "filter", contentType = "filter") {
                SegmentedControl(
                    options = listOf(AppFilter.USER, AppFilter.SYSTEM, AppFilter.ALL),
                    selected = settings.filter,
                    onSelect = vm::setFilter,
                    modifier = Modifier.padding(bottom = 16.dp),
                ) { option, active ->
                    val tint by animateColorAsState(
                        if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "filter",
                    )
                    Text(
                        when (option) {
                            AppFilter.USER -> "Installed"
                            AppFilter.SYSTEM -> "System"
                            AppFilter.ALL -> "All"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = tint,
                    )
                }
            }
            if (settings.showTips && !state.loading && state.apps.isNotEmpty()) {
                item(key = "tip", contentType = "tip") {
                    TipCard(onDismiss = vm::dismissTips, modifier = Modifier.padding(bottom = 16.dp).animateItem())
                }
            }

            item(key = "count", contentType = "count") {
                Text(
                    if (state.loading) "Loading apps…"
                    else "${state.apps.size} ${if (state.apps.size == 1) "app" else "apps"}  ·  ${rememberFileSize(state.totalBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
                )
            }

            when {
                state.loading -> items(6, contentType = { "skeleton" }) { SkeletonRow(Modifier.padding(bottom = 8.dp)) }

                state.error != null && state.apps.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Couldn't load apps",
                        message = "Something went wrong. Please try again.",
                    ) { PrimaryButton("Try again", onClick = vm::retry, height = 46.dp) }
                }

                state.apps.isEmpty() && query.isNotBlank() -> item {
                    EmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No results",
                        message = "Nothing matches “${query.trim()}”",
                    ) {
                        PrimaryButton(
                            if (settings.filter != AppFilter.ALL) "Search all apps" else "Clear search",
                            onClick = {
                                if (settings.filter != AppFilter.ALL) vm.setFilter(AppFilter.ALL) else query = ""
                            },
                            height = 46.dp,
                        )
                    }
                }

                state.apps.isEmpty() -> item {
                    EmptyState(icon = Icons.Rounded.Apps, title = "No apps here", message = "Pull down to refresh.")
                }

                else -> items(state.apps, key = { it.packageName }, contentType = { "app" }) { app ->
                    AppRow(
                        app = app,
                        selected = app.packageName in selection,
                        selectionMode = inSelection,
                        onClick = {
                            if (inSelection) vm.toggleSelection(app.packageName) else onOpenApp(app.packageName)
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.toggleSelection(app.packageName)
                        },
                        onQuickExtract = { onExtract(listOf(app.packageName)) },
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showScrollTop,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 14.dp + 54.dp + 14.dp + keyboardLift),
    ) {
        ScrollTopButton {
            scope.launch {
                // Jump most of the way instantly, then glide the last stretch.
                if (listState.firstVisibleItemIndex > 20) listState.scrollToItem(12)
                listState.animateScrollToItem(0)
            }
        }
    }

    AnimatedVisibility(
        visible = !inSelection,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp + keyboardLift),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, { query = it }, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            SortButton(settings.sortBy, settings.sortAscending, vm::setSort)
        }
    }
    }
}

/**
 * A small dark pill centred above the search dock — high contrast and away from the per-row
 * download buttons, so it can't be mistaken for one.
 */
@Composable
private fun ScrollTopButton(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier
            .height(38.dp)
            .semantics { contentDescription = "Scroll to top" },
    ) {
        Row(Modifier.padding(start = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("Top", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TipCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                "Tap an app to see details. Press and hold to select several at once.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss tip", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    Surface(
        shape = CircleShape,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 8.dp,
        modifier = modifier.height(54.dp),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search apps",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SortButton(sortBy: SortBy, ascending: Boolean, onSort: (SortBy) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(54.dp)
                .semantics { contentDescription = "Sort" },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            SortBy.entries.forEach { option ->
                val active = option == sortBy
                DropdownMenuItem(
                    text = {
                        Text(
                            option.label,
                            style = if (active) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                            color = if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onSort(option)
                        open = false
                    },
                    trailingIcon = {
                        if (active) {
                            Icon(
                                if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                contentDescription = if (ascending) "Ascending" else "Descending",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** One app in its own soft card. Plain background + clip only — no shadow or border — so it stays cheap. */
@Composable
private fun AppRow(
    app: AppInfo,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickExtract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // A plain colour (no per-row animation state) keeps scrolling through hundreds of rows cheap.
    val background = if (selected) colors.primaryContainer else colors.surfaceContainerLowest
    Row(
        modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(background)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = if (selected) "Deselect" else "Select",
            )
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, app.iconKey, 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${versionLabel(app).take(18)}  ·  ${rememberFileSize(app.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                FormatTag(packageFormat(app))
            }
        }
        Spacer(Modifier.width(8.dp))
        if (selectionMode) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { SelectBadge(selected) }
        } else {
            TonalIconButton(Icons.Rounded.Download, "Extract ${app.label}", onClick = onQuickExtract)
        }
    }
}

private val CardShape = RoundedCornerShape(20.dp)

@Composable
private fun SelectBadge(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "badge")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, colors.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = selected, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(17.dp))
        }
    }
}
