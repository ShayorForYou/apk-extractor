package com.shayor.apkextractor.ui.screens

import com.shayor.apkextractor.ui.components.ToastTone
import com.shayor.apkextractor.ui.components.toast
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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
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
import com.shayor.apkextractor.data.ThemeMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.shayor.apkextractor.ui.theme.appColors

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
    val c = appColors
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
                // The faint blue glow of the app's canvas, high behind the first card.
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(c.glow, Color.Transparent),
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width * 0.95f,
                        ),
                        radius = size.width * 0.95f,
                        center = Offset(size.width / 2f, 0f),
                    )
                }
                .verticalScroll(scroll)
                .padding(contentPadding)
                .padding(top = topBarHeight() + 12.dp),
        ) {
            AboutCard()

            SettingsLabel("Appearance")
            SettingsGroup {
                Column(Modifier.padding(12.dp)) {
                    SettingsThemeSelector(settings.themeMode, vm::setThemeMode)
                }
                SettingsDivider()
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingIcon(Icons.Rounded.Palette)
                        Spacer(Modifier.width(RowIconGap))
                        Text("Accent color", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(12.dp))
                    AccentPicker(selected = settings.accent, onSelect = vm::setAccent)
                }
                SettingsDivider(IconRowInset)
                SettingsToggleRow(
                    Icons.Rounded.DarkMode,
                    "Pure black",
                    "True black for OLED screens in dark mode",
                    settings.pureBlack,
                    vm::setPureBlack,
                )
            }

            SettingsLabel("Saving")
            SettingsGroup {
                SettingsNavRow(
                    "Save to",
                    icon = Icons.Rounded.FolderOpen,
                    value = settings.outputFolderName ?: ApkExporter.DEFAULT_LOCATION,
                    onClick = {
                        try {
                            folderPicker.launch(null)
                        } catch (_: ActivityNotFoundException) {
                            scope.launch { snackbar.toast("Can't pick a folder on this device", ToastTone.Warning) }
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
                SettingsDivider(IconRowInset)
                SettingsNavRow("Split apps", icon = Icons.Rounded.Layers, value = settings.splitMode.label, onClick = { picker = "split" })
                SettingsDivider(IconRowInset)
                SettingsNavRow("File name", icon = Icons.Rounded.DriveFileRenameOutline, subtitle = exampleName(settings.nameFormat), onClick = { picker = "name" })
                SettingsDivider(IconRowInset)
                SettingsNavRow(
                    "Auto-backup",
                    icon = Icons.Rounded.Autorenew,
                    value = when (autoBackupApps.size) {
                        0 -> "Off"
                        1 -> "1 app"
                        else -> "${autoBackupApps.size} apps"
                    },
                    onClick = { picker = "autobackup" },
                )
            }
            SettingsFooter(
                if (autoBackupApps.isEmpty()) {
                    "Turn on auto-backup for an app in its details, and each new version is saved when it updates."
                } else {
                    "Each new version of these apps is saved when it updates."
                },
            )

            SettingsLabel("Storage")
            SettingsGroup {
                SettingsNavRow(
                    "Clear cache",
                    icon = Icons.Rounded.DeleteSweep,
                    iconTint = if (cacheBytes > 0) c.bad else c.textMuted,
                    value = if (cacheBytes > 0) rememberFileSize(cacheBytes) else "Empty",
                    titleColor = if (cacheBytes > 0) c.bad else c.textMuted,
                    chevron = false,
                    enabled = cacheBytes > 0,
                    onClick = {
                        vm.clearShareCache()
                        scope.launch { snackbar.toast("Cache cleared", ToastTone.Success) }
                    },
                )
            }
            SettingsFooter("Files prepared for sharing are kept here until you clear them.")
        }

        GlassTopBar("Settings", collapsed = collapsed)
    }

    // After the save folder changes: offers to move the saved files there.
    MoveFilesHost(vm)

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

// ---- Settings pieces -------------------------------------------------------------------------------

/** Small tracked caps above a group. */
@Composable
private fun SettingsLabel(text: String) {
    Text(
        text.uppercase(),
        color = appColors.textMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.8.sp,
        modifier = Modifier.padding(start = 6.dp, top = 26.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsFooter(text: String) {
    Text(
        text,
        color = appColors.textMuted,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
    )
}

/** A flat card edged by a hairline. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val c = appColors
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.hairline, shape),
        content = content,
    )
}

/**
 * What a setting is about at a glance: an outline glyph on a soft tile of its own tint, the same
 * for every row so the list stays calm (only a destructive row turns red).
 */
@Composable
private fun SettingIcon(icon: ImageVector, tint: Color = appColors.accent) {
    Box(
        Modifier
            .size(RowIconSize)
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = if (appColors.dark) 0.16f else 0.10f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

private val RowIconSize = 30.dp
private val RowIconGap = 12.dp

/** Dividers between rows with icons start under the text, as in iOS Settings. */
private val IconRowInset = 16.dp + RowIconSize + RowIconGap

@Composable
private fun SettingsDivider(start: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(1.dp)
            .background(appColors.hairline),
    )
}

@Composable
private fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconTint: Color = appColors.accent,
    value: String? = null,
    subtitle: String? = null,
    titleColor: Color = appColors.textPrimary,
    chevron: Boolean = true,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = appColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingIcon(icon, iconTint)
            Spacer(Modifier.width(RowIconGap))
        }
        Column(if (value == null) Modifier.weight(1f) else Modifier.widthIn(max = 200.dp)) {
            Text(title, color = titleColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                color = c.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = appColors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onCheckedChange(it)
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f)) {
            Text(title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            // One line, so the icon lines up with the text as on every other row.
            if (subtitle != null) Text(subtitle, color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        MiniSwitch(checked)
    }
}

/** Compact switch: purely visual, the row toggles. */
@Composable
private fun MiniSwitch(checked: Boolean) {
    val c = appColors
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 520f), label = "switch")
    Canvas(Modifier.size(width = 40.dp, height = 24.dp)) {
        val h = size.height
        drawRoundRect(lerp(c.track, c.accent, t), cornerRadius = CornerRadius(h / 2f))
        val r = h / 2f - 2.5.dp.toPx()
        val x = h / 2f + (size.width - h) * t
        drawCircle(Color.Black.copy(alpha = 0.12f), r, Offset(x, h / 2f + 0.8.dp.toPx()))
        drawCircle(Color.White, r, Offset(x, h / 2f))
    }
}

/** Auto / Light / Dark with a sliding, springy pill. */
@Composable
private fun SettingsThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = appColors
    val options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val labels = listOf("Auto", "Light", "Dark")
    val pill = RoundedCornerShape(50)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(pill)
            .background(c.surfaceVariant)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            segment * options.indexOf(selected).coerceAtLeast(0),
            spring(dampingRatio = 0.75f, stiffness = 450f),
            label = "themePill",
        )
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .shadow(1.5.dp, pill)
                .background(if (c.dark) c.surface else Color.White, pill),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, mode ->
                val active = mode == selected
                val tint by animateColorAsState(if (active) c.textPrimary else c.textMuted, label = "themeTint")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pill)
                        .selectable(selected = active, role = Role.RadioButton) { onSelect(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(labels[i], color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
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

/** The app's logo, name and version, as a card with a tracked caps caption. */
@Composable
private fun AboutCard() {
    val c = appColors
    SettingsGroup {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppLogo(size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("APK Extractor", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "VERSION ${BuildConfig.VERSION_NAME}",
                    color = c.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
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
