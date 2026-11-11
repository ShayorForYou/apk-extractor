package com.shayor.apkextractor.ui

import android.Manifest
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shayor.apkextractor.data.ApkExporter
import com.shayor.apkextractor.data.SplitMode
import com.shayor.apkextractor.ui.components.AppLogo
import com.shayor.apkextractor.ui.components.AppSnackbarHost
import com.shayor.apkextractor.ui.components.UndoVisuals
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay
import com.shayor.apkextractor.ui.components.Panel
import com.shayor.apkextractor.ui.screens.AppDetailsSheet
import com.shayor.apkextractor.ui.screens.AppsScreen
import com.shayor.apkextractor.ui.screens.ExtractResultSheet
import com.shayor.apkextractor.ui.screens.HistoryScreen
import com.shayor.apkextractor.ui.screens.InstallScreen
import com.shayor.apkextractor.ui.screens.InstallSheet
import com.shayor.apkextractor.ui.screens.SettingsScreen
import com.shayor.apkextractor.ui.screens.TaskProgressDialog
import kotlinx.coroutines.launch

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
    val task by vm.task.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val appsByPackage by vm.appsByPackage.collectAsStateWithLifecycle()
    val autoBackupApps by vm.autoBackupApps.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(Tab.APPS) }
    var detailsPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<UiEvent.Extracted?>(null) }
    // Tabs live in a pager: swipe between them, or tap the bottom bar to glide there.
    val pager = rememberPagerState(initialPage = tab.ordinal) { Tab.entries.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.targetPage }.collect { tab = Tab.entries[it] }
    }
    val goTo: (Tab) -> Unit = { t -> scope.launch { pager.animateScrollToPage(t.ordinal) } }

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
                pager.scrollToPage(Tab.INSTALL.ordinal)
                lifecycleOwner.lifecycle.withResumed { pickFile() }
            }
            Shortcut.BACKUP_ALL -> pager.scrollToPage(Tab.APPS.ordinal)
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

    val pagePadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp)

    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.background),
        ) {
            BrandBar()
            Box(Modifier.weight(1f)) {
                HorizontalPager(
                    state = pager,
                    // Keep the neighbouring tab composed so a swipe never waits for it to build.
                    beyondViewportPageCount = 1,
                    key = { Tab.entries[it].name },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (Tab.entries[page]) {
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
                        Tab.SETTINGS -> SettingsScreen(vm, snackbar, pagePadding)
                    }
                }
                AppSnackbarHost(
                    snackbar,
                    Modifier
                        .align(Alignment.BottomCenter)
                        // Clear the floating search dock on the Apps tab.
                        .padding(bottom = if (tab == Tab.APPS && selection.isEmpty()) 80.dp else 8.dp),
                )
            }

            // Docked bottom bar: tabs, or actions while selecting apps.
            Surface(color = colors.surfaceContainerLowest) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = colors.outlineVariant)
                    AnimatedContent(
                        targetState = selection.isNotEmpty() && tab == Tab.APPS,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { it / 2 } + fadeOut())
                        },
                        label = "bottom",
                    ) { selecting ->
                        if (selecting) {
                            SelectionBar(
                                count = selection.size,
                                onClear = vm::clearSelection,
                                onSelectAll = vm::selectAllVisible,
                                onShare = { vm.share(selection) },
                                onExtract = { requestExtract(selection) },
                            )
                        } else {
                            BottomNav(tab, onSelect = goTo)
                        }
                    }
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
                saveLocation = settings.outputFolderName ?: ApkExporter.DEFAULT_LOCATION,
                onDismiss = { detailsPkg = null },
                onExtract = { mode -> extractWith(listOf(pkg), mode) },
                onShare = { mode ->
                    detailsPkg = null
                    vm.share(listOf(pkg), mode)
                },
                onAutoBackupChange = { on -> vm.setAutoBackup(pkg, on) },
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

    if (installState !is InstallState.Idle) InstallSheet(installState, canInstall, installVm, onPickFile = pickFile)

    (task as? TaskState.Running)?.let { TaskProgressDialog(it, onCancel = vm::cancelTask) }
}

// -------------------------------------------------------------------------------------------------
// Chrome
// -------------------------------------------------------------------------------------------------

/** Slim top bar shown on every tab: the app's logo and name. The theme lives in Settings. */
@Composable
private fun BrandBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text("APK Extractor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 12.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { t ->
            val active = t == current
            val pill by animateColorAsState(if (active) colors.primaryContainer else Color.Transparent, label = "navPill")
            val iconTint by animateColorAsState(if (active) colors.primary else colors.onSurfaceVariant, label = "navIcon")
            val textTint by animateColorAsState(if (active) colors.onSurface else colors.onSurfaceVariant, label = "navText")
            val pillWidth by animateDpAsState(if (active) 60.dp else 40.dp, label = "navPillWidth")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(t) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .width(pillWidth)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(pill),
                    contentAlignment = Alignment.Center,
                ) { Icon(t.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.height(4.dp))
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = textTint)
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onExtract: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainerLowest,
        contentColor = colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") }
            Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
            Surface(
                onClick = onExtract,
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.height(48.dp),
            ) {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Extract", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
