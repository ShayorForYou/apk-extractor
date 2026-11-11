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
import android.text.format.DateUtils
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Update
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(
    vm: MainViewModel,
    contentPadding: PaddingValues,
    onExtract: (Collection<String>) -> Unit,
    onShare: (String) -> Unit,
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

    // Start the list from the top when the filter / sort / query changes.
    val listKey = "${settings.filter}|${settings.sortBy}|${settings.sortAscending}|$query"
    var lastListKey by rememberSaveable { mutableStateOf(listKey) }
    var scrollPending by remember { mutableStateOf(false) }
    LaunchedEffect(listKey) {
        if (listKey != lastListKey) {
            lastListKey = listKey
            scrollPending = true
            delay(400)
            if (scrollPending && listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
            scrollPending = false
        }
    }
    LaunchedEffect(state.apps) {
        if (scrollPending) {
            if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
            scrollPending = false
        }
    }

    // The floating dock sits just above the docked tab bar, and above the keyboard while typing.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val tabBarPx = with(density) { 77.dp.roundToPx() }
    val keyboardLift = with(density) { (imeBottom - navBottom - tabBarPx).coerceAtLeast(0).toDp() }

    // Scroll-to-top: appears only after a deliberate upward scroll (a real distance, not jitter),
    // hides the moment the user scrolls down again, and fades away by itself after a short idle.
    val scope = rememberCoroutineScope()
    var wantsScrollTop by remember { mutableStateOf(false) }
    val scrollDirection = remember(density) {
        val showAfterPx = with(density) { 64.dp.toPx() }
        val hideAfterPx = with(density) { 8.dp.toPx() }
        object : NestedScrollConnection {
            private var upDistance = 0f
            private var downDistance = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy > 0f) { // content moving down = user scrolling up
                    upDistance += dy
                    downDistance = 0f
                    if (upDistance > showAfterPx) wantsScrollTop = true
                } else if (dy < 0f) {
                    downDistance -= dy
                    upDistance = 0f
                    if (downDistance > hideAfterPx) wantsScrollTop = false
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(wantsScrollTop, listState.isScrollInProgress) {
        if (wantsScrollTop && !listState.isScrollInProgress) {
            delay(2500)
            wantsScrollTop = false
        }
    }
    val farDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 8 } }
    val showScrollTop = wantsScrollTop && farDown && !inSelection && !imeVisible

    val showTip = settings.showTips && !state.loading && state.apps.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // Pinned header: the filter and the count stay in view; only the list below scrolls.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            SegmentedControl(
                options = listOf(AppFilter.USER, AppFilter.SYSTEM, AppFilter.ALL),
                selected = settings.filter,
                onSelect = vm::setFilter,
                modifier = Modifier.padding(bottom = 12.dp),
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
            Text(
                if (state.loading) "Loading apps…"
                else "${state.apps.size} ${if (state.apps.size == 1) "app" else "apps"}  ·  ${rememberFileSize(state.totalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
        }

    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 100.dp),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollDirection),
        ) {
            if (showTip) {
                item(key = "tip", contentType = "tip") {
                    TipCard(onDismiss = vm::dismissTips, modifier = Modifier.padding(bottom = 12.dp).animateItem())
                }
            }

            when {
                state.loading -> items(6, contentType = { "skeleton" }) { SkeletonRow(Modifier.padding(bottom = 8.dp)) }

                state.error != null && state.apps.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Couldn't load apps",
                        message = "Something went wrong. Please try again.",
                    ) { PrimaryButton("Try again", onClick = vm::retry, height = 44.dp) }
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
                            height = 44.dp,
                        )
                    }
                }

                state.apps.isEmpty() -> item {
                    EmptyState(icon = Icons.Rounded.Apps, title = "No apps here", message = "Pull down to refresh.")
                }

                else -> items(state.apps, key = { it.packageName }, contentType = { "app" }) { app ->
                    AppRow(
                        app = app,
                        sortBy = settings.sortBy,
                        selected = app.packageName in selection,
                        selectionMode = inSelection,
                        query = state.query,
                        onShare = { onShare(app.packageName) },
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

    if (!state.loading) {
        FastScroller(
            listState = listState,
            apps = state.apps,
            firstAppIndex = if (showTip) 1 else 0, // (tip)
            sortBy = settings.sortBy,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Clear of the floating search dock at the bottom.
                .padding(top = 4.dp, bottom = 14.dp + 54.dp + 24.dp + keyboardLift),
        )
    }
    } // list area
    } // column

    AnimatedVisibility(
        visible = showScrollTop,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 14.dp + 54.dp + 14.dp + keyboardLift),
    ) {
        ScrollTopButton {
            wantsScrollTop = false
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
                "Tap an app to see details, swipe it right to share, or press and hold to select several.",
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
    val colors = MaterialTheme.colorScheme
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
        // A floating layer, clearly above the list: its own surface tone, a hairline border and a
        // deep shadow; the current choice is an accent pill that says which way it sorts.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = colors.surfaceContainerHighest,
            shadowElevation = 16.dp,
            border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.35f)),
            modifier = Modifier.width(252.dp),
        ) {
            Text(
                "SORT BY",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 8.dp),
            )
            SortBy.entries.forEach { option ->
                val active = option == sortBy
                Row(
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(role = Role.RadioButton) {
                            onSort(option)
                            open = false
                        }
                        .semantics { this.selected = active }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        option.icon(),
                        contentDescription = null,
                        tint = if (active) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) colors.primary else colors.onSurface,
                        )
                        if (active) {
                            Text(
                                directionLabel(option, ascending),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (active) {
                        Icon(
                            if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = directionLabel(option, ascending),
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Text(
                "Tap the current sort again to reverse it",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            )
        }
    }
}

private fun SortBy.icon(): ImageVector = when (this) {
    SortBy.NAME -> Icons.Rounded.SortByAlpha
    SortBy.SIZE -> Icons.Rounded.Storage
    SortBy.INSTALLED -> Icons.Rounded.Event
    SortBy.UPDATED -> Icons.Rounded.Update
}

private fun directionLabel(sortBy: SortBy, ascending: Boolean): String = when (sortBy) {
    SortBy.NAME -> if (ascending) "A to Z" else "Z to A"
    SortBy.SIZE -> if (ascending) "Smallest first" else "Largest first"
    SortBy.INSTALLED, SortBy.UPDATED -> if (ascending) "Oldest first" else "Newest first"
}

/**
 * One app in its own soft card. Plain background + clip only — no shadow or border — so it stays cheap.
 * Swipe right to share it. Only rightward drags are claimed, so a leftward swipe still moves the pager
 * to the next tab and vertical drags still scroll the list.
 */
@Composable
private fun AppRow(
    app: AppInfo,
    selected: Boolean,
    selectionMode: Boolean,
    query: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickExtract: () -> Unit,
    onShare: () -> Unit,
    sortBy: SortBy,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { SwipeThreshold.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    val swiping by remember { derivedStateOf { dragX > 0.5f } }
    val armed by remember { derivedStateOf { dragX >= thresholdPx } }
    val currentOnShare by rememberUpdatedState(onShare)

    // A plain colour (no per-row animation state) keeps scrolling through hundreds of rows cheap.
    val background = if (selected) colors.primaryContainer else colors.surfaceContainerLowest
    val titleHighlight = colors.primary.copy(alpha = 0.16f)
    val edge = if (selected) colors.primary.copy(alpha = 0.35f) else colors.outlineVariant.copy(alpha = 0.45f)
    val shape = CardShape

    Box(modifier.padding(bottom = 8.dp)) {
        if (swiping) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(if (armed) colors.primary else colors.primaryContainer)
                    .padding(start = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = null,
                    tint = if (armed) colors.onPrimary else colors.primary,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            val s = (dragX / thresholdPx).coerceIn(0.6f, 1.1f)
                            scaleX = s
                            scaleY = s
                        },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragX }
                .clip(shape)
                .background(background)
                .border(1.dp, edge, shape)
                .pointerInput(selectionMode) {
                    if (selectionMode) return@pointerInput
                    var release: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var start = 0f
                        val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            if (over > 0f) {
                                change.consume()
                                start = over
                            }
                        } ?: return@awaitEachGesture
                        release?.cancel()
                        var raw = start
                        var wasArmed = false
                        dragX = resist(raw, thresholdPx)
                        val completed = horizontalDrag(drag.id) { change ->
                            raw = (raw + change.positionChange().x).coerceAtLeast(0f)
                            change.consume()
                            dragX = resist(raw, thresholdPx)
                            val nowArmed = raw >= thresholdPx
                            if (nowArmed && !wasArmed) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            wasArmed = nowArmed
                        }
                        if (completed && wasArmed) currentOnShare()
                        release = scope.launch {
                            animate(dragX, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                                dragX = v
                            }
                        }
                    }
                }
                .semantics {
                    this.selected = selected
                    if (!selectionMode) {
                        customActions = listOf(CustomAccessibilityAction("Share") { currentOnShare(); true })
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = if (selected) "Deselect" else "Select",
                )
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, app.iconKey, 46.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val title = remember(app.label, query, titleHighlight) { highlight(app.label, query, titleHighlight) }
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A search that only matches the package name shows that name, so the hit makes sense.
                    val packageHit = query.isNotEmpty() &&
                        !app.label.contains(query, ignoreCase = true) &&
                        app.packageName.contains(query, ignoreCase = true)
                    val subtitle = if (packageHit) {
                        remember(app.packageName, query, titleHighlight) { highlight(app.packageName, query, titleHighlight) }
                    } else {
                        // Sorted by a date: show that date, so the order explains itself.
                        val detail = when (sortBy) {
                            SortBy.INSTALLED -> "Installed ${rememberRelativeDay(app.firstInstallTime)}"
                            SortBy.UPDATED -> "Updated ${rememberRelativeDay(app.lastUpdateTime)}"
                            else -> rememberFileSize(app.sizeBytes)
                        }
                        AnnotatedString("${versionLabel(app).take(18)}  ·  $detail")
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Only split apps are tagged; a plain APK is the norm and needs no label.
                    if (app.isSplit) {
                        Spacer(Modifier.width(8.dp))
                        FormatTag(packageFormat(app))
                    }
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
}

/** Each app is its own softly outlined card, set 8dp apart. */
private val CardShape = RoundedCornerShape(18.dp)

/** "today", "yesterday", "3 days ago", or a date for older ones; cached per row. */
@Composable
private fun rememberRelativeDay(time: Long): String = remember(time) {
    val now = System.currentTimeMillis()
    DateUtils.getRelativeTimeSpanString(time, now, DateUtils.DAY_IN_MILLIS).toString()
        .let { if (it == "Today" || it == "Yesterday") it.lowercase() else it }
}

private val SwipeThreshold = 96.dp

/** Past the threshold the row follows the finger more and more reluctantly. */
private fun resist(x: Float, threshold: Float): Float =
    if (x <= threshold) x else threshold + (x - threshold) * 0.25f

/** [text] with the first case-insensitive match of [query] tinted, or plain text when there is none. */
private fun highlight(text: String, query: String, background: Color): AnnotatedString {
    val start = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(background = background, fontWeight = FontWeight.Bold), start, start + query.length)
    }
}

// -------------------------------------------------------------------------------------------------
// Fast scroller
// -------------------------------------------------------------------------------------------------

/**
 * A draggable thumb on the right edge for long lists. Shown while the list scrolls (and briefly
 * after); dragging it jumps through the list and shows where you are: the first letter when sorted
 * by name, otherwise the size or date being sorted by. Its touch strip is narrow and sits outside
 * the rows' buttons.
 */
@Composable
private fun FastScroller(
    listState: LazyListState,
    apps: List<AppInfo>,
    firstAppIndex: Int,
    sortBy: SortBy,
    modifier: Modifier = Modifier,
) {
    if (apps.size < FAST_SCROLL_MIN_APPS) return
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(1_500)
            visible = false
        }
    }
    val listFraction by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val scrollable = info.totalItemsCount - info.visibleItemsInfo.size
            if (scrollable <= 0) 0f else (listState.firstVisibleItemIndex.toFloat() / scrollable).coerceIn(0f, 1f)
        }
    }
    val dateFormat = remember { java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()) }
    fun labelAt(fraction: Float): String {
        val app = apps[(fraction * (apps.size - 1)).roundToInt().coerceIn(0, apps.size - 1)]
        return when (sortBy) {
            SortBy.NAME -> app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() }?.toString() ?: "#"
            SortBy.SIZE -> android.text.format.Formatter.formatShortFileSize(context, app.sizeBytes)
            SortBy.INSTALLED -> dateFormat.format(java.util.Date(app.firstInstallTime))
            SortBy.UPDATED -> dateFormat.format(java.util.Date(app.lastUpdateTime))
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxHeight().width(FastScrollBubbleWidth + FastScrollTouchWidth)) {
            val density = LocalDensity.current
            val trackPx = constraints.maxHeight.toFloat()
            val thumbPx = with(density) { FastScrollThumbHeight.toPx() }
            val travel = (trackPx - thumbPx).coerceAtLeast(1f)
            val shown = if (dragging) dragFraction else listFraction
            val thumbY = (shown * travel).roundToInt()
            var lastLabel by remember { mutableStateOf("") }

            fun jumpTo(y: Float) {
                dragFraction = ((y - thumbPx / 2) / travel).coerceIn(0f, 1f)
                val index = firstAppIndex + (dragFraction * (apps.size - 1)).roundToInt()
                val label = labelAt(dragFraction)
                if (label != lastLabel) {
                    lastLabel = label
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
                scope.launch { listState.scrollToItem(index) }
            }

            // Touch strip: only the right-most part, clear of the rows' download buttons.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(FastScrollTouchWidth)
                    .pointerInput(apps, firstAppIndex) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                jumpTo(offset.y)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            change.consume()
                            jumpTo(change.position.y)
                        }
                    }
                    .semantics { contentDescription = "Fast scroll" },
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(0, thumbY) }
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                        .width(if (dragging) 8.dp else 5.dp)
                        .height(FastScrollThumbHeight)
                        .clip(CircleShape)
                        .background(if (dragging) colors.primary else colors.outline.copy(alpha = 0.7f)),
                )
            }

            if (dragging) {
                Box(
                    Modifier
                        .offset { IntOffset(0, (thumbY + thumbPx / 2 - with(density) { 28.dp.toPx() }).roundToInt()) }
                        .align(Alignment.TopEnd)
                        .padding(end = FastScrollTouchWidth + 2.dp)
                        .height(56.dp)
                        .widthIn(min = 56.dp, max = FastScrollBubbleWidth)
                        .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp))
                        .background(colors.primary)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labelAt(dragFraction),
                        style = if (sortBy == SortBy.NAME) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge,
                        color = colors.onPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private const val FAST_SCROLL_MIN_APPS = 40
private val FastScrollTouchWidth = 24.dp
private val FastScrollBubbleWidth = 120.dp
private val FastScrollThumbHeight = 44.dp

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
