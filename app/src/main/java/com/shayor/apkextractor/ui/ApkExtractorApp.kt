package com.shayor.apkextractor.ui

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shayor.apkextractor.data.ApkExporter
import com.shayor.apkextractor.data.SplitMode
import com.shayor.apkextractor.ui.components.AppSnackbarHost
import com.shayor.apkextractor.ui.components.Chrome
import com.shayor.apkextractor.ui.components.CloseButton
import com.shayor.apkextractor.ui.components.LocalGlassKey
import com.shayor.apkextractor.ui.components.LocalGlassState
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.ShareButton
import com.shayor.apkextractor.ui.components.SplitFormatMenu
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.UndoVisuals
import com.shayor.apkextractor.ui.components.glass
import com.shayor.apkextractor.ui.components.pressScale
import com.shayor.apkextractor.ui.components.tabBarClearance
import com.shayor.apkextractor.ui.screens.AppDetailsSheet
import com.shayor.apkextractor.ui.screens.AppsScreen
import com.shayor.apkextractor.ui.screens.ExtractResultSheet
import com.shayor.apkextractor.ui.screens.HistoryScreen
import com.shayor.apkextractor.ui.screens.InstallScreen
import com.shayor.apkextractor.ui.screens.InstallSheet
import com.shayor.apkextractor.ui.screens.SettingsScreen
import com.shayor.apkextractor.ui.screens.TaskProgressHost
import com.shayor.apkextractor.ui.theme.iosColors
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tab crossfade: the new tab fades in a touch slower than the old one fades out. */
private const val TAB_FADE_IN_MS = 180
private const val TAB_FADE_OUT_MS = 110

/** A file that takes longer than this to read opens the install sheet with placeholders. */
private const val SLOW_READ_MS = 300L

/** How long a removal can be undone (before accessibility adjustments). */
private const val UNDO_WINDOW_MS = 6_000L

private enum class Tab(val label: String, val icon: ImageVector) {
    APPS("Apps", Icons.Rounded.GridView),
    INSTALL("Install", Icons.Rounded.InstallMobile),
    HISTORY("History", Icons.Rounded.History),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
fun ApkExtractorApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }
    val accessibility = LocalAccessibilityManager.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val appsByPackage by vm.appsByPackage.collectAsStateWithLifecycle()
    val autoBackupApps by vm.autoBackupApps.collectAsStateWithLifecycle()
    val autoBackupFormats by vm.autoBackupFormats.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.APPS) }
    var detailsPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<UiEvent.Extracted?>(null) }
    // Tabs switch only from the tab bar (as on iOS and in Material bottom navigation): no sideways
    // swipe, so horizontal gestures inside a screen (share, delete) never fight with the tabs.
    val goTo: (Tab) -> Unit = { t -> tab = t }
    // Each tab keeps its scroll position, search text etc. while another tab is showing.
    val tabStates = rememberSaveableStateHolder()

    // --- Installing APKs ---------------------------------------------------------------------
    val installVm: InstallViewModel = viewModel()
    val installState by installVm.state.collectAsStateWithLifecycle()
    val canInstall by installVm.canInstall.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) installVm.open(uri)
    }
    val pickFile: () -> Unit = {
        try {
            // Bundles (.apks/.xapk/.apkm) have no standard MIME type, so every file is offered and checked.
            filePicker.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No file picker found") }
        }
    }
    LaunchedEffect(installVm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            installVm.launch.collect { intent ->
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    launch { snackbar.showSnackbar("No app can open this") }
                }
            }
        }
    }
    // Launcher shortcuts: "Install APK" opens the file picker, "Back up all apps" shows the
    // selection of every installed app (the ViewModel selects them) so one tap extracts them.
    val shortcut by vm.shortcut.collectAsStateWithLifecycle()
    LaunchedEffect(shortcut) {
        when (shortcut ?: return@LaunchedEffect) {
            Shortcut.INSTALL -> {
                tab = Tab.INSTALL
                lifecycleOwner.lifecycle.withResumed { pickFile() }
            }
            Shortcut.BACKUP_ALL -> tab = Tab.APPS
        }
        vm.consumeShortcut()
    }

    // Back from Settings (install permission) or an uninstall: refresh what we know.
    LaunchedEffect(installVm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { installVm.refreshPermission() }
    }

    // One-shot events are only handled while the UI is visible.
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.events.collect { event ->
                when (event) {
                    is UiEvent.Message -> {
                        snackbar.currentSnackbarData?.dismiss()
                        launch { snackbar.showSnackbar(event.text) }
                    }

                    is UiEvent.Undoable -> {
                        snackbar.currentSnackbarData?.dismiss()
                        // Longer with accessibility services on (e.g. TalkBack), as Android recommends.
                        val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                            UNDO_WINDOW_MS, containsIcons = true, containsText = true, containsControls = true,
                        ) ?: UNDO_WINDOW_MS
                        val visuals = UndoVisuals(event.text, timeout)
                        launch {
                            val timer = launch {
                                delay(timeout)
                                if (snackbar.currentSnackbarData?.visuals === visuals) snackbar.currentSnackbarData?.dismiss()
                            }
                            val result = snackbar.showSnackbar(visuals)
                            timer.cancel()
                            if (result == SnackbarResult.ActionPerformed) event.undo()
                        }
                    }

                    is UiEvent.Launch -> try {
                        context.startActivity(event.intent)
                    } catch (_: ActivityNotFoundException) {
                        launch { snackbar.showSnackbar("No app can open this") }
                    }

                    is UiEvent.LaunchAny -> {
                        val started = event.intents.any { intent ->
                            runCatching { context.startActivity(intent) }.isSuccess
                        }
                        if (!started) launch { snackbar.showSnackbar(event.failMessage) }
                    }

                    is UiEvent.Extracted -> {
                        detailsPkg = null
                        result = event
                    }
                }
            }
        }
    }

    // Android 9 and older need a runtime permission to write to Downloads.
    var pendingExtract by remember { mutableStateOf<Pair<List<String>, SplitMode?>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = pendingExtract
        pendingExtract = null
        if (granted && pending != null) vm.extract(pending.first, pending.second)
        else if (!granted) scope.launch { snackbar.showSnackbar("Storage permission is needed to save apps") }
    }
    val extractWith: (Collection<String>, SplitMode?) -> Unit = { pkgs, mode ->
        if (vm.needsLegacyStoragePermission()) {
            pendingExtract = pkgs.toList() to mode
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            vm.extract(pkgs, mode)
        }
    }
    val requestExtract: (Collection<String>) -> Unit = { pkgs -> extractWith(pkgs, null)
    }

    BackHandler(enabled = tab != Tab.APPS) { goTo(Tab.APPS) }

    // Pages run edge to edge under the glass bars; each adds its own top bar height. The bottom
    // keeps the last rows clear of the floating tab bar.
    val pagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = tabBarClearance() + 28.dp)
    val glass = rememberHazeState()
    val selecting = selection.isNotEmpty() && tab == Tab.APPS

    CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalGlassState provides glass) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            // A quick crossfade between tabs. Only the visible tab is composed (and blurred), apart
            // from the moment both are fading.
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(TAB_FADE_IN_MS)) togetherWith fadeOut(tween(TAB_FADE_OUT_MS)) },
                label = "tab",
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                tabStates.SaveableStateProvider(shown.name) {
                CompositionLocalProvider(LocalGlassKey provides shown) {
                when (shown) {
                    Tab.APPS -> AppsScreen(
                        vm = vm,
                        contentPadding = pagePadding,
                        onExtract = requestExtract,
                        onShare = { pkg -> vm.share(listOf(pkg)) },
                        onOpenApp = { detailsPkg = it },
                    )
                    Tab.INSTALL -> InstallScreen(vm, installVm, pagePadding, onPickFile = pickFile)
                    Tab.HISTORY -> HistoryScreen(
                        vm,
                        pagePadding,
                        onBrowseApps = { goTo(Tab.APPS) },
                        onInstall = installVm::open,
                    )
                    Tab.SETTINGS -> SettingsScreen(
                        vm,
                        snackbar,
                        pagePadding,
                        onOpenApp = { detailsPkg = it },
                        onBrowseApps = { goTo(Tab.APPS) },
                    )
                }
                }
                }
            }

            AppSnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    // Above the tab bar, and above the search field on the Apps tab.
                    .padding(bottom = tabBarClearance() + if (tab == Tab.APPS && !selecting) 74.dp else 12.dp),
            )

            // Floating glass tab bar; while apps are selected it morphs into their toolbar.
            AnimatedContent(
                targetState = selecting,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.92f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.92f)) using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.BottomCenter,
                label = "bottom",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = Chrome.FloatingMargin, end = Chrome.FloatingMargin, bottom = Chrome.TabBarGap),
            ) { selectingNow ->
                if (selectingNow) {
                    SelectionBar(
                        count = selection.size,
                        hasSplit = selection.any { appsByPackage[it]?.isSplit == true },
                        onClear = vm::clearSelection,
                        onSelectAll = vm::selectAllVisible,
                        onShare = { mode -> vm.share(selection, mode) },
                        defaultSplitMode = settings.splitMode,
                        onExtract = { mode -> extractWith(selection, mode) },
                    )
                } else {
                    GlassTabBar(tab, onSelect = goTo)
                }
            }
        }
    }

    detailsPkg?.let { pkg ->
        val app = appsByPackage[pkg]
        if (app != null) {
            AppDetailsSheet(
                app = app,
                splitMode = settings.splitMode,
                autoBackup = pkg in autoBackupApps,
                backupFormat = autoBackupFormats[pkg],
                saveLocation = settings.outputFolderName ?: ApkExporter.DEFAULT_LOCATION,
                onDismiss = { detailsPkg = null },
                onExtract = { mode -> extractWith(listOf(pkg), mode) },
                onShare = { mode ->
                    detailsPkg = null
                    vm.share(listOf(pkg), mode)
                },
                onAutoBackupChange = { on, format -> vm.setAutoBackup(pkg, on, format) },
                onBackupFormatChange = { format -> vm.setAutoBackupFormat(pkg, format) },
            )
        } else if (appsByPackage.isNotEmpty()) {
            LaunchedEffect(pkg) { detailsPkg = null } // uninstalled while open
        }
    }

    result?.let { r ->
        ExtractResultSheet(
            result = r,
            onDismiss = { result = null },
            onOpenFolder = {
                result = null
                vm.openFolder(r.treeUri)
            },
            onShare = {
                result = null
                vm.shareFiles(r.saved.map { it.file })
            },
        )
    }

    // Open the sheet once the file has been read, so it slides up a single time with its final
    // content. Only a slow read (a big bundle) shows the sheet early, with placeholders.
    var showInstallSheet by remember { mutableStateOf(false) }
    LaunchedEffect(installState is InstallState.Idle, installState is InstallState.Reading) {
        showInstallSheet = when (installState) {
            InstallState.Idle -> false
            InstallState.Reading -> {
                delay(SLOW_READ_MS)
                true
            }
            else -> true
        }
    }
    if (showInstallSheet && installState !is InstallState.Idle) {
        InstallSheet(installState, canInstall, installVm, onPickFile = pickFile)
    }

    TaskProgressHost(vm.task, onCancel = vm::cancelTask)
}

// -------------------------------------------------------------------------------------------------
// Chrome
// -------------------------------------------------------------------------------------------------

/** How close the lens at [position] is to tab [index]: 1 on it, fading to 0 one tab away. */
private fun closeness(position: Float, index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

/**
 * The iOS 26 tab bar: a floating glass capsule. A soft "lens" sits behind the current tab and glides
 * to a newly tapped one, and each tab's accent colour fades in as the lens arrives. It all moves in
 * the layout / draw phases, so the bar never recomposes while animating.
 */
@Composable
private fun GlassTabBar(current: Tab, onSelect: (Tab) -> Unit) {
    // The lens glides to the chosen tab on a spring; read only in layout / draw, so it never recomposes.
    val lensPosition = animateFloatAsState(
        current.ordinal.toFloat(),
        spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "lens",
    )
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val lens = if (p.dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(Chrome.TabBarHeight)
            .glass(CircleShape)
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / Tab.entries.size
        Box(
            Modifier
                .offset {
                    val position = lensPosition.value
                    IntOffset((position * itemWidth.toPx()).roundToInt(), 0)
                }
                .width(itemWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(lens),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            Tab.entries.forEachIndexed { i, t ->
                val source = remember { MutableInteractionSource() }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressScale(source, pressed = 0.88f)
                        .selectable(
                            selected = current.ordinal == i,
                            role = Role.Tab,
                            interactionSource = source,
                            indication = null,
                            onClick = { onSelect(t) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Two copies, label colour and accent; the accent one fades with the lens.
                    Box {
                        Icon(t.icon, contentDescription = null, tint = p.label, modifier = Modifier.size(24.dp))
                        Icon(
                            t.icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { alpha = closeness(lensPosition.value, i) },
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Box {
                        Text(t.label, style = TabLabel, color = p.label, maxLines = 1)
                        Text(
                            t.label,
                            style = TabLabel,
                            color = accent,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = closeness(lensPosition.value, i) },
                        )
                    }
                }
            }
        }
    }
}

private val TabLabel = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)

/** Glass toolbar shown instead of the tabs while apps are selected. */
@Composable
private fun SelectionBar(
    count: Int,
    hasSplit: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: (SplitMode?) -> Unit,
    defaultSplitMode: SplitMode,
    onExtract: (SplitMode?) -> Unit,
) {
    // Like Share: with split apps in the selection, ask how to save them instead of silently
    // using the Settings default. Without any, extract straight away.
    var askFormat by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(Chrome.TabBarHeight)
            .glass(CircleShape)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloseButton(onClear, description = "Clear selection")
        Spacer(Modifier.width(12.dp))
        // The number rolls like a counter as apps are added or removed.
        AnimatedContent(
            count,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                    (slideOutVertically { if (up) -it else it } + fadeOut())
            },
            label = "count",
            modifier = Modifier.weight(1f),
        ) { n ->
            Text("$n selected", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
        TonalIconButton(Icons.Rounded.SelectAll, "Select all", onClick = onSelectAll, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        ShareButton(hasSplit, onShare = onShare, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        Box {
            PrimaryButton(
                "Extract",
                onClick = { if (hasSplit) askFormat = true else onExtract(null) },
                icon = Icons.Rounded.Download,
                height = 48.dp,
            )
            SplitFormatMenu(
                askFormat,
                onDismiss = { askFormat = false },
                title = "Extract Split Apps As",
                default = defaultSplitMode.takeIf { it != SplitMode.BASE_ONLY },
                onPick = { mode ->
                    askFormat = false
                    onExtract(mode)
                },
            )
        }
    }
}
