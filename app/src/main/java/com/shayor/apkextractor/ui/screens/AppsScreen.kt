package com.shayor.apkextractor.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.data.AppFilter
import com.shayor.apkextractor.data.PackageType
import com.shayor.apkextractor.data.AppInfo
import com.shayor.apkextractor.data.SortBy
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.ActivityIndicator
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.Chrome
import com.shayor.apkextractor.ui.components.CloseButton
import com.shayor.apkextractor.ui.components.EmptyState
import com.shayor.apkextractor.ui.components.FormatTag
import com.shayor.apkextractor.ui.components.GlassTopBar
import com.shayor.apkextractor.ui.components.GroupShape
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.IosMenu
import com.shayor.apkextractor.ui.components.IosMenuItem
import com.shayor.apkextractor.ui.components.PillButton
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.SegmentLabel
import com.shayor.apkextractor.ui.components.SegmentedControl
import com.shayor.apkextractor.ui.components.SkeletonRow
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.cellColor
import com.shayor.apkextractor.ui.components.glass
import com.shayor.apkextractor.ui.components.glassSource
import com.shayor.apkextractor.ui.components.groupItemShape
import com.shayor.apkextractor.ui.components.packageFormat
import com.shayor.apkextractor.ui.components.pressScale
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.scrolledUnderBar
import com.shayor.apkextractor.ui.components.tabBarClearance
import com.shayor.apkextractor.ui.components.topBarHeight
import com.shayor.apkextractor.ui.components.versionLabel
import com.shayor.apkextractor.ui.theme.iosColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The filter control pinned under the Apps title: 34dp control + 10dp below it. */
private val FilterRowHeight = 44.dp

/** The floating search field above the tab bar, and its gap to the tab bar. */
private val SearchHeight = 50.dp
private val SearchGap = 10.dp

/** Where row text starts, so dividers line up under it (16 + 44dp icon + 14). */
private val RowTextInset = 74.dp

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
    val autoBackupApps by vm.autoBackupApps.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val collapsed by listState.scrolledUnderBar()

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
    val listKey = "${settings.filter}|${settings.packageType}|${settings.sortBy}|${settings.sortAscending}|$query"
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

    // The search field floats just above the tab bar, and above the keyboard while typing.
    val density = LocalDensity.current
    val tabBar = tabBarClearance()
    val imeBottom = with(density) { WindowInsets.ime.getBottom(this).toDp() }
    val keyboardLift = (imeBottom - tabBar).coerceAtLeast(0.dp)
    val searchBottom = tabBar + SearchGap + keyboardLift
    val topPad = topBarHeight(FilterRowHeight)

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
    val refreshState = rememberPullToRefreshState()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .glassSource()
                .clipToBounds(),
        ) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refresh,
                state = refreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    RefreshIndicator(
                        refreshState,
                        refreshing,
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topPad + 10.dp),
                    )
                },
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topPad + 4.dp,
                        bottom = searchBottom + SearchHeight + 24.dp,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        // Like iOS, the list itself slides down to make room for the spinner.
                        .graphicsLayer { translationY = refreshState.distanceFraction.coerceIn(0f, 1.4f) * 56.dp.toPx() }
                        .nestedScroll(scrollDirection),
                ) {
                    if (showTip) {
                        item(key = "tip", contentType = "tip") {
                            TipCard(onDismiss = vm::dismissTips, modifier = Modifier.padding(top = 8.dp).animateItem())
                        }
                    }

                    item(key = "count", contentType = "count") {
                        Row(
                            Modifier.padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (state.loading) "LOADING APPS…"
                                else "${state.apps.size} ${if (state.apps.size == 1) "APP" else "APPS"}  ·  ${rememberFileSize(state.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = iosColors.secondaryLabel,
                                modifier = Modifier.weight(1f),
                            )
                            PackageTypeButton(settings.packageType, vm::setPackageType)
                        }
                    }

                    when {
                        state.loading -> items(6, contentType = { "skeleton" }) { i ->
                            SkeletonRow(shape = groupItemShape(i == 0, i == 5))
                        }

                        state.error != null && state.apps.isEmpty() -> item {
                            EmptyState(
                                icon = Icons.Rounded.CloudOff,
                                title = "Couldn't Load Apps",
                                message = "Something went wrong. Please try again.",
                            ) { PrimaryButton("Try Again", onClick = vm::retry, height = 44.dp) }
                        }

                        state.apps.isEmpty() && query.isNotBlank() -> item {
                            EmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = "No Results",
                                message = "Nothing matches “${query.trim()}”",
                            ) {
                                PrimaryButton(
                                    if (settings.filter != AppFilter.ALL) "Search All Apps" else "Clear Search",
                                    onClick = {
                                        if (settings.filter != AppFilter.ALL) vm.setFilter(AppFilter.ALL) else query = ""
                                    },
                                    height = 44.dp,
                                )
                            }
                        }

                        state.apps.isEmpty() && settings.packageType != PackageType.ALL -> item {
                            EmptyState(
                                icon = Icons.Rounded.Layers,
                                title = "No ${settings.packageType.label}",
                                message = "None of these apps match. Try another filter.",
                            ) {
                                PrimaryButton("Show All Types", onClick = { vm.setPackageType(PackageType.ALL) }, height = 44.dp)
                            }
                        }

                        state.apps.isEmpty() -> item {
                            EmptyState(icon = Icons.Rounded.Apps, title = "No Apps Here", message = "Pull down to refresh.")
                        }

                        else -> itemsIndexed(state.apps, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { i, app ->
                            AppRow(
                                app = app,
                                first = i == 0,
                                last = i == state.apps.lastIndex,
                                sortBy = settings.sortBy,
                                selected = app.packageName in selection,
                                autoBackup = app.packageName in autoBackupApps,
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
                    firstAppIndex = if (showTip) 2 else 1, // (tip), count
                    sortBy = settings.sortBy,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = topPad + 8.dp, bottom = searchBottom + SearchHeight + 12.dp),
                )
            }
        }

        GlassTopBar(
            title = "Apps",
            collapsed = collapsed,
            bottom = {
                SegmentedControl(
                    options = listOf(AppFilter.USER, AppFilter.SYSTEM, AppFilter.ALL),
                    selected = settings.filter,
                    onSelect = vm::setFilter,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                ) { option, active ->
                    SegmentLabel(
                        when (option) {
                            AppFilter.USER -> "Installed"
                            AppFilter.SYSTEM -> "System"
                            AppFilter.ALL -> "All"
                        },
                        active,
                    )
                }
            },
        )

        AnimatedVisibility(
            visible = showScrollTop,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = searchBottom + SearchHeight + 12.dp),
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
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = Chrome.FloatingMargin, end = Chrome.FloatingMargin, bottom = searchBottom),
        ) {
            // Search and sort side by side, as a pair of glass controls (iOS 26 bottom toolbar).
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SortButton(settings.sortBy, settings.sortAscending, vm::setSort)
            }
        }
    }
}

/**
 * iOS pull-down button for the package type: a small capsule showing the current choice. It turns
 * accent-tinted while a filter is on, so a shortened list always explains itself.
 */
@Composable
private fun PackageTypeButton(type: PackageType, onChange: (PackageType) -> Unit) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val active = type != PackageType.ALL
    val source = remember { MutableInteractionSource() }
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(30.dp)
                .pressScale(source, 0.94f)
                .clip(CircleShape)
                .background(if (active) accent.copy(alpha = if (p.dark) 0.24f else 0.13f) else p.tertiaryFill)
                .clickable(source, indication = null, role = Role.Button, onClickLabel = "Choose package type") { open = true }
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                type.label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (active) accent else p.label,
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = if (active) accent else p.secondaryLabel, modifier = Modifier.size(16.dp))
        }
        IosMenu(open, onDismiss = { open = false }, title = "Show") {
            PackageType.entries.forEachIndexed { i, option ->
                IosMenuItem(
                    option.label,
                    onClick = {
                        open = false
                        onChange(option)
                    },
                    icon = when (option) {
                        PackageType.ALL -> Icons.Rounded.Apps
                        PackageType.SPLIT -> Icons.Rounded.Layers
                        PackageType.SINGLE -> Icons.Rounded.Android
                    },
                    subtitle = option.description,
                    checked = option == type,
                    divider = i > 0,
                )
            }
        }
    }
}

/** UIRefreshControl: spokes appear as you pull, then spin while refreshing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val pulled = state.distanceFraction >= 1f
    Box(
        modifier
            .size(36.dp)
            .graphicsLayer { alpha = if (refreshing) 1f else (state.distanceFraction * 1.6f).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center,
    ) {
        ActivityIndicator(size = 26.dp, spinning = refreshing || pulled, revealed = { state.distanceFraction })
    }
}

/** A small glass capsule centred above the search field, clearly apart from the rows' buttons. */
@Composable
private fun ScrollTopButton(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Row(
        Modifier
            .height(38.dp)
            .pressScale(source, 0.9f)
            .glass(CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Scroll to top" }
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text("Top", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TipCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(cellColor())
            .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(Icons.Rounded.TouchApp, MaterialTheme.colorScheme.primary, size = 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Quick Tips", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "Tap an app to see details, swipe it right to share, or touch and hold to select several.",
                style = MaterialTheme.typography.bodySmall,
                color = iosColors.secondaryLabel,
            )
        }
        Spacer(Modifier.width(8.dp))
        CloseButton(onDismiss, description = "Dismiss tip")
    }
}

/** Glass search capsule, like the search field iOS 26 places at the bottom of the screen. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val p = iosColors
    val focusManager = LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .height(SearchHeight)
            .glass(CircleShape)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = p.secondaryLabel, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search", style = MaterialTheme.typography.bodyLarge, color = p.secondaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.label),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = "Clear search",
                tint = p.tertiaryLabel,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") }
                    .padding(6.dp),
            )
        }
    }
}

/** A glass circle beside the search field that opens an iOS-style pull-down menu. */
@Composable
private fun SortButton(sortBy: SortBy, ascending: Boolean, onSort: (SortBy) -> Unit) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    Box {
        Box(
            Modifier
                .size(SearchHeight)
                .pressScale(source, 0.9f)
                .glass(CircleShape)
                .clickable(source, indication = null, role = Role.Button, onClickLabel = "Sort") { open = true }
                .semantics { contentDescription = "Sort" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(22.dp),
            containerColor = if (p.dark) Color(0xFF2C2C2E) else Color(0xFFF9F9FB),
            shadowElevation = 18.dp,
            border = BorderStroke(0.5.dp, p.separator),
            modifier = Modifier.width(260.dp),
        ) {
            Text(
                "Sort By",
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryLabel,
                modifier = Modifier.padding(start = 18.dp, top = 4.dp, bottom = 6.dp),
            )
            SortBy.entries.forEachIndexed { i, option ->
                val active = option == sortBy
                if (i > 0) Box(Modifier.padding(start = 46.dp).fillMaxWidth().height(0.5.dp).background(p.separator))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) {
                            onSort(option)
                            open = false
                        }
                        .semantics { this.selected = active }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // iOS menus mark the current choice with a leading checkmark.
                    Box(Modifier.width(30.dp)) {
                        if (active) Icon(Icons.Rounded.Check, contentDescription = null, tint = p.label, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                        if (active) {
                            Text(directionLabel(option, ascending), style = MaterialTheme.typography.bodySmall, color = p.secondaryLabel)
                        }
                    }
                    if (active) {
                        Icon(
                            if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = directionLabel(option, ascending),
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(option.icon(), contentDescription = null, tint = p.label, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                "Choose the current sort again to reverse it.",
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryLabel,
                modifier = Modifier.padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
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
 * One app: a row of the inset grouped list (outer corners rounded, hairline divider under the text).
 * Swipe right to share, like an iOS leading swipe action. Only rightward drags are claimed, so a
 * leftward swipe still moves the pager to the next tab and vertical drags still scroll the list.
 * In selection mode, iOS edit-mode circles slide in on the left.
 */
@Composable
private fun AppRow(
    app: AppInfo,
    first: Boolean,
    last: Boolean,
    selected: Boolean,
    /** Auto-backup is on: a small green arrow after the version, as in Settings. */
    autoBackup: Boolean,
    selectionMode: Boolean,
    query: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQuickExtract: () -> Unit,
    onShare: () -> Unit,
    sortBy: SortBy,
    modifier: Modifier = Modifier,
) {
    val p = iosColors
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { SwipeThreshold.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    val swiping by remember { derivedStateOf { dragX > 0.5f } }
    val armed by remember { derivedStateOf { dragX >= thresholdPx } }
    val currentOnShare by rememberUpdatedState(onShare)

    val cell = cellColor()
    val selectedTint = colors.primary.copy(alpha = if (p.dark) 0.22f else 0.10f)
    val titleHighlight = colors.primary.copy(alpha = 0.18f)
    val separator = p.separator
    val shape = groupItemShape(first, last)

    Box(modifier.clip(shape)) {
        if (swiping) {
            Row(
                Modifier
                    .matchParentSize()
                    .background(if (armed) colors.primary else colors.primary.copy(alpha = 0.75f))
                    .padding(start = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Share,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val s = (dragX / thresholdPx).coerceIn(0.6f, 1.1f)
                            scaleX = s
                            scaleY = s
                        },
                )
                Spacer(Modifier.width(8.dp))
                Text("Share", style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = dragX }
                .background(cell)
                .then(if (selected) Modifier.background(selectedTint) else Modifier)
                .drawBehind {
                    if (!first) {
                        val x = (if (selectionMode) RowTextInset + 36.dp else RowTextInset).toPx()
                        drawRect(separator, topLeft = Offset(x, 0f), size = Size(size.width - x, 0.5.dp.toPx()))
                    }
                }
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
                            animate(dragX, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
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
                .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectCircle(selected)
                Spacer(Modifier.width(12.dp))
            }
            AppIcon(app.packageName, app.iconKey, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val title = remember(app.label, query, titleHighlight) { highlight(app.label, query, titleHighlight) }
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                        color = p.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (autoBackup) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Rounded.Autorenew,
                            contentDescription = "Auto-backup on",
                            tint = p.green,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    // Only split apps are tagged; a plain APK is the norm and needs no label.
                    if (app.isSplit) {
                        Spacer(Modifier.width(6.dp))
                        FormatTag(packageFormat(app))
                    }
                }
            }
            if (!selectionMode) {
                Spacer(Modifier.width(10.dp))
                PillButton(
                    "",
                    onClick = onQuickExtract,
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.semantics { contentDescription = "Extract ${app.label}" },
                )
            }
        }
    }
}

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
    val p = iosColors
    val context = LocalContext.current
    val density = LocalDensity.current
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
            val trackPx = constraints.maxHeight.toFloat()
            val thumbPx = with(density) { FastScrollThumbHeight.toPx() }
            val travel = (trackPx - thumbPx).coerceAtLeast(1f)
            val shown = if (dragging) dragFraction else listFraction
            val thumbY = (shown * travel).roundToInt()
            var lastLabel by remember { mutableStateOf("") }
            val bubbleOffset = with(density) { 28.dp.toPx() }

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
                        .padding(end = 3.dp)
                        .width(if (dragging) 7.dp else 4.dp)
                        .height(FastScrollThumbHeight)
                        .clip(CircleShape)
                        .background(if (dragging) colors.primary else p.label.copy(alpha = 0.35f)),
                )
            }

            if (dragging) {
                Box(
                    Modifier
                        .offset { IntOffset(0, (thumbY + thumbPx / 2 - bubbleOffset).roundToInt()) }
                        .align(Alignment.TopEnd)
                        .padding(end = FastScrollTouchWidth + 2.dp)
                        .height(56.dp)
                        .widthIn(min = 56.dp, max = FastScrollBubbleWidth)
                        .glass(CircleShape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labelAt(dragFraction),
                        style = if (sortBy == SortBy.NAME) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge,
                        color = colors.primary,
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

/** The iOS edit-mode selection circle. */
@Composable
private fun SelectCircle(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val p = iosColors
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(colors.primary)
                else Modifier.border(1.5.dp, p.tertiaryLabel, CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
    }
}
