package com.shayor.apkextractor.ui.screens

import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.BuildConfig
import com.shayor.apkextractor.data.ApkExporter
import com.shayor.apkextractor.data.FileNames
import com.shayor.apkextractor.data.NameFormat
import com.shayor.apkextractor.data.SplitMode
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.ScreenHeader
import com.shayor.apkextractor.ui.components.PanelColumn
import com.shayor.apkextractor.ui.components.AccentPicker
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.ThemeSelector
import com.shayor.apkextractor.ui.components.rememberFileSize
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, snackbar: SnackbarHostState, contentPadding: PaddingValues) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cacheBytes by vm.shareCacheBytes.collectAsStateWithLifecycle()
    val autoBackupApps by vm.autoBackupApps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    var picker by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.refreshShareCacheSize() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setOutputFolder(uri)
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        ScreenHeader(title = "Settings")

            Section("Appearance")
            Group {
                ThemeSelector(settings.themeMode, vm::setThemeMode, Modifier.padding(12.dp))
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)) {
                    Text("Accent color", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    AccentPicker(selected = settings.accent, onSelect = vm::setAccent)
                }
                RowDivider()
                SwitchRow(Icons.Rounded.Contrast, "Pure black", settings.pureBlack, vm::setPureBlack)
            }

            Section("Saving")
            Group {
                NavRow(
                    icon = Icons.Rounded.Folder,
                    title = "Save to",
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
                            IconButton(onClick = vm::resetOutputFolder, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Use default folder",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                RowDivider()
                NavRow(Icons.Rounded.Layers, "Split apps", settings.splitMode.label, onClick = { picker = "split" })
                RowDivider()
                NavRow(
                    Icons.Rounded.DriveFileRenameOutline,
                    "File name",
                    exampleName(settings.nameFormat),
                    onClick = { picker = "name" },
                )
                RowDivider()
                NavRow(
                    Icons.Rounded.Autorenew,
                    "Auto-backup",
                    when (autoBackupApps.size) {
                        0 -> "Off. Turn it on for an app in its details"
                        1 -> "1 app is saved whenever it updates"
                        else -> "${autoBackupApps.size} apps are saved whenever they update"
                    },
                    onClick = null,
                    showChevron = false,
                )
            }

            Section("Other")
            Group {
                NavRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear cache",
                    value = if (cacheBytes > 0) rememberFileSize(cacheBytes) else "Empty",
                    enabled = cacheBytes > 0,
                    showChevron = false,
                    onClick = {
                        vm.clearShareCache()
                        scope.launch { snackbar.showSnackbar("Cache cleared") }
                    },
                )
                RowDivider()
                NavRow(Icons.Rounded.Info, "Version", BuildConfig.VERSION_NAME, onClick = null, showChevron = false)
            }
        }

    when (picker) {
        "split" -> ChoiceSheet(
            title = "Split apps",
            options = SplitMode.entries,
            selected = settings.splitMode,
            label = { it.label },
            description = { it.description },
            onSelect = vm::setSplitMode,
            onDismiss = { picker = null },
        )

        "name" -> ChoiceSheet(
            title = "File name",
            options = NameFormat.entries,
            selected = settings.nameFormat,
            label = { exampleName(it) },
            description = { null },
            onSelect = vm::setNameFormat,
            onDismiss = { picker = null },
        )
    }
}

private fun exampleName(format: NameFormat) =
    FileNames.build("WhatsApp", "com.whatsapp", "2.24.1", format, "apk")

// -------------------------------------------------------------------------------------------------
// Building blocks
// -------------------------------------------------------------------------------------------------

@Composable
private fun Section(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 10.dp),
    )
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    PanelColumn(content = content)
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = colors.onPrimary,
                uncheckedTrackColor = colors.surfaceContainerHigh,
                uncheckedBorderColor = colors.surfaceContainerHigh,
                uncheckedThumbColor = colors.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(enabled = enabled, onClick = onClick) else base)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
        if (showChevron && onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.outline)
        }
    }
}

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
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
            Group {
                Column(Modifier.selectableGroup()) {
                    options.forEachIndexed { i, option ->
                        if (i > 0) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = colors.outlineVariant.copy(alpha = 0.7f),
                            )
                        }
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
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(label(option), style = MaterialTheme.typography.titleSmall)
                                description(option)?.let {
                                    Spacer(Modifier.height(2.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                            if (active) {
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.tertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}
