package com.shayor.apkextractor.ui.screens

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shayor.apkextractor.ui.components.versionLabel
import com.shayor.apkextractor.ui.components.relativeTime
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.IosSwitch
import com.shayor.apkextractor.ui.components.EmptyState
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.data.AppInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.Android
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.BuildConfig
import com.shayor.apkextractor.data.ApkExporter
import com.shayor.apkextractor.data.FileNames
import com.shayor.apkextractor.data.NameFormat
import com.shayor.apkextractor.data.SplitMode
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.AccentPicker
import com.shayor.apkextractor.ui.components.AppLogo
import com.shayor.apkextractor.ui.components.Cell
import com.shayor.apkextractor.ui.components.CellDivider
import com.shayor.apkextractor.ui.components.GlassTopBar
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.InsetGroup
import com.shayor.apkextractor.ui.components.IosSheet
import com.shayor.apkextractor.ui.components.SectionFooter
import com.shayor.apkextractor.ui.components.SectionHeader
import com.shayor.apkextractor.ui.components.SheetHeader
import com.shayor.apkextractor.ui.components.SwitchCell
import com.shayor.apkextractor.ui.components.ThemeSelector
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.glassSource
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.scrolledUnderBar
import com.shayor.apkextractor.ui.components.topBarHeight
import com.shayor.apkextractor.ui.theme.iosColors
import kotlinx.coroutines.launch

/** Inset for row dividers that start after a 30dp icon tile, as in iOS Settings. */
private val IconRowInset = 60.dp

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    contentPadding: PaddingValues,
    onOpenApp: (String) -> Unit,
    onBrowseApps: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cacheBytes by vm.shareCacheBytes.collectAsStateWithLifecycle()
    val autoBackupApps by vm.autoBackupApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val p = iosColors
    val scroll = rememberScrollState()
    val collapsed by scroll.scrolledUnderBar()

    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshShareCacheSize() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setOutputFolder(uri)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource()
                .verticalScroll(scroll)
                .padding(contentPadding)
                .padding(top = topBarHeight() + 8.dp),
        ) {
            AboutCard()

            SectionHeader("Appearance")
            InsetGroup {
                ThemeSelector(settings.themeMode, vm::setThemeMode, Modifier.padding(12.dp))
                CellDivider()
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                    Text("Accent Color", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    AccentPicker(selected = settings.accent, onSelect = vm::setAccent)
                }
                CellDivider()
                SwitchCell(
                    "Pure Black",
                    settings.pureBlack,
                    vm::setPureBlack,
                    icon = { IconTile(Icons.Rounded.Contrast, p.gray) },
                )
            }
            SectionFooter("Pure Black uses true black in dark mode, which saves power on OLED screens.")

            SectionHeader("Saving")
            InsetGroup {
                Cell(
                    "Save To",
                    icon = { IconTile(Icons.Rounded.Folder, p.blue) },
                    value = settings.outputFolderName ?: ApkExporter.DEFAULT_LOCATION,
                    onClick = {
                        try {
                            folderPicker.launch(null)
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbar.showSnackbar("Can't pick a folder on this device") }
                        }
                    },
                    trailing = if (settings.outputTreeUri != null) {
                        {
                            TonalIconButton(
                                Icons.AutoMirrored.Rounded.Undo,
                                "Use default folder",
                                onClick = vm::resetOutputFolder,
                                size = 28.dp,
                            )
                        }
                    } else {
                        null
                    },
                )
                CellDivider(IconRowInset)
                Cell(
                    "Split Apps",
                    icon = { IconTile(Icons.Rounded.Layers, p.purple) },
                    value = settings.splitMode.label,
                    onClick = { picker = "split" },
                )
                CellDivider(IconRowInset)
                Cell(
                    "File Name",
                    icon = { IconTile(Icons.Rounded.DriveFileRenameOutline, p.orange) },
                    subtitle = exampleName(settings.nameFormat),
                    onClick = { picker = "name" },
                )
                CellDivider(IconRowInset)
                Cell(
                    "Auto-Backup",
                    icon = { IconTile(Icons.Rounded.Autorenew, p.green) },
                    value = when (autoBackupApps.size) {
                        0 -> "Off"
                        1 -> "1 app"
                        else -> "${autoBackupApps.size} apps"
                    },
                    onClick = { picker = "autobackup" },
                )
            }
            SectionFooter(
                if (autoBackupApps.isEmpty()) {
                    "Turn on auto-backup for an app in its details, and each new version is saved when it updates."
                } else {
                    "Each new version of these apps is saved when it updates."
                },
            )

            SectionHeader("Storage")
            InsetGroup {
                Cell(
                    "Clear Cache",
                    icon = { IconTile(Icons.Rounded.DeleteSweep, p.red) },
                    value = if (cacheBytes > 0) rememberFileSize(cacheBytes) else "Empty",
                    enabled = cacheBytes > 0,
                    chevron = false,
                    onClick = {
                        vm.clearShareCache()
                        scope.launch { snackbar.showSnackbar("Cache cleared") }
                    },
                )
            }
            SectionFooter("Files prepared for sharing are kept here until you clear them.")
        }

        GlassTopBar("Settings", collapsed = collapsed)
    }

    when (picker) {
        "split" -> ChoiceSheet(
            title = "Split Apps",
            options = SplitMode.entries,
            selected = settings.splitMode,
            label = { it.label },
            description = { it.description },
            onSelect = vm::setSplitMode,
            onDismiss = { picker = null },
        )

        "name" -> ChoiceSheet(
            title = "File Name",
            options = NameFormat.entries,
            selected = settings.nameFormat,
            label = { exampleName(it) },
            description = { null },
            onSelect = vm::setNameFormat,
            onDismiss = { picker = null },
        )

        "autobackup" -> AutoBackupSheet(
            vm,
            onOpenApp = { pkg ->
                picker = null
                onOpenApp(pkg)
            },
            onBrowseApps = {
                picker = null
                onBrowseApps()
            },
            onDismiss = { picker = null },
        )
    }
}

/**
 * The apps with auto-backup on, each with its own switch. Rows stay put while the sheet is open, so
 * an app switched off by mistake can be switched straight back on. Tapping a row opens the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoBackupSheet(
    vm: MainViewModel,
    onOpenApp: (String) -> Unit,
    onBrowseApps: () -> Unit,
    onDismiss: () -> Unit,
) {
    val watched by vm.autoBackupApps.collectAsStateWithLifecycle()
    val apps by vm.appsByPackage.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val formats by vm.autoBackupFormats.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Formats as they were on opening: an app switched off and back on here keeps its own.
    val openingFormats = remember { formats }
    // The list as it was when the sheet opened (plus any added since), sorted by name.
    var shown by remember { mutableStateOf(watched) }
    LaunchedEffect(watched) { shown = shown + watched }
    // Latest save per app; its name also stands in for apps that are no longer installed.
    val latest = remember(history) {
        history.groupBy { it.packageName }.mapValues { (_, entries) -> entries.maxBy { it.time } }
    }
    val rows = remember(shown, apps, latest) {
        shown.map { pkg -> pkg to apps[pkg] }
            .sortedBy { (pkg, app) -> (app?.label ?: latest[pkg]?.label ?: pkg).lowercase() }
    }

    IosSheet(onDismiss) {
        SheetHeader("Auto-Backup", onClose = onDismiss)
        if (rows.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Autorenew,
                title = "No Apps Yet",
                message = "Turn on Auto-Backup Updates in an app's details, and each new version is saved when it updates.",
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                PrimaryButton("Browse Apps", onClick = onBrowseApps, height = 44.dp)
            }
        } else {
            Column(
                Modifier
                    .sheetScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            ) {
                InsetGroup {
                    rows.forEachIndexed { i, (pkg, app) ->
                        if (i > 0) CellDivider(start = 70.dp)
                        AutoBackupRow(
                            pkg = pkg,
                            app = app,
                            savedLabel = latest[pkg]?.label,
                            lastSaved = latest[pkg]?.time,
                            // Split apps only: the others are always a single APK.
                            format = if (app?.isSplit == true) formats[pkg] ?: openingFormats[pkg] ?: settings.splitMode else null,
                            on = pkg in watched,
                            onToggle = { on -> vm.setAutoBackup(pkg, on, openingFormats[pkg]) },
                            onOpen = { onOpenApp(pkg) },
                        )
                    }
                }
                SectionFooter("Each new version of these apps is saved when it updates. Saved copies are in History.")
            }
        }
    }
}

@Composable
private fun AutoBackupRow(
    pkg: String,
    app: AppInfo?,
    savedLabel: String?,
    lastSaved: Long?,
    format: SplitMode?,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val p = iosColors
    val label = app?.label ?: savedLabel ?: pkg
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (app != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) {
            AppIcon(app.packageName, app.iconKey, size = 40.dp)
        } else {
            IconTile(Icons.Rounded.Android, p.gray, size = 40.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val formatLabel = when (format) {
                SplitMode.BUNDLE -> "  ·  APKS"
                SplitMode.MERGE -> "  ·  APK"
                SplitMode.BASE_ONLY -> "  ·  Base APK"
                null -> ""
            }
            val detail = when {
                app == null -> "Not installed · Resumes if it's reinstalled"
                lastSaved != null -> "${versionLabel(app).take(18)}$formatLabel  ·  Saved ${relativeTime(lastSaved).lowercase()}"
                else -> "${versionLabel(app).take(18)}$formatLabel"
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = p.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        IosSwitch(
            on,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = "Auto-backup for $label" },
        )
    }
}

/** Like the account card at the top of iOS Settings: the app's icon, name and version. */
@Composable
private fun AboutCard() {
    InsetGroup {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppLogo(size = 60.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("APK Extractor", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Version ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = iosColors.secondaryLabel,
                )
            }
        }
    }
}

private fun exampleName(format: NameFormat) =
    FileNames.build("WhatsApp", "com.whatsapp", "2.24.1", format, "apk")

/** An iOS picker sheet: a grouped list with a checkmark beside the current choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    IosSheet(onDismiss) {
        SheetHeader(title, onClose = onDismiss)
        InsetGroup(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { i, option ->
                    if (i > 0) CellDivider()
                    val active = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = active,
                                onClick = {
                                    onSelect(option)
                                    onDismiss()
                                },
                                role = Role.RadioButton,
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            description(option)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = iosColors.secondaryLabel)
                            }
                        }
                        Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterEnd) {
                            if (active) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = accent)
                        }
                    }
                }
            }
        }
    }
}
