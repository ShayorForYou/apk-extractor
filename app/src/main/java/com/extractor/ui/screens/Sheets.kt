package com.extractor.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.extractor.data.AppInfo
import com.extractor.data.SplitMode
import com.extractor.ui.UiEvent
import com.extractor.ui.components.ActionTile
import com.extractor.ui.components.AppIcon
import com.extractor.ui.components.Eyebrow
import com.extractor.ui.components.Panel
import com.extractor.ui.components.PrimaryButton
import com.extractor.ui.components.SecondaryButton
import com.extractor.ui.components.TonalIconButton
import com.extractor.ui.components.rememberFileSize
import com.extractor.ui.components.versionLabel
import com.extractor.ui.components.FormatTag
import com.extractor.ui.components.packageFormat
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) { content() }
}

// -------------------------------------------------------------------------------------------------
// App details
// -------------------------------------------------------------------------------------------------

@Composable
fun AppDetailsSheet(
    app: AppInfo,
    splitMode: SplitMode,
    onDismiss: () -> Unit,
    onExtract: (SplitMode) -> Unit,
    onShare: (SplitMode) -> Unit,
) {
    val context = LocalContext.current
    // Split apps can be saved either way; start from the default chosen in Settings.
    var format by rememberSaveable(app.packageName) {
        mutableStateOf(if (splitMode == SplitMode.BUNDLE) SplitMode.BUNDLE else SplitMode.MERGE)
    }
    val mode = if (app.isSplit) format else splitMode
    val colors = MaterialTheme.colorScheme
    val launchIntent = remember(app.packageName, app.lastUpdateTime) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
    }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Sheet(onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, app.iconKey, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${versionLabel(app).take(20)}  •  ${rememberFileSize(app.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    FormatTag(
                        packageFormat(app),
                        detail = if (app.isSplit) "${app.splitSourceDirs.size + 1} PARTS" else null,
                    )
                }
            }

            if (app.isSplit) {
                Spacer(Modifier.height(22.dp))
                FormatPicker(format, onSelect = { format = it })
            }

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    if (app.isSplit && format == SplitMode.BUNDLE) "Extract APKS" else "Extract APK",
                    onClick = { onExtract(mode) },
                    modifier = Modifier.weight(1f),
                )
                TonalIconButton(Icons.Rounded.Share, "Share", onClick = { onShare(mode) }, size = 56.dp)
            }

            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxWidth()) {
                Column {
                    InfoRow("Package", app.packageName, onClick = { copyToClipboard(context, app.packageName) }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = colors.onSurfaceVariant, modifier = Modifier.size(15.dp))
                    }
                    InfoDivider()
                    InfoRow("Installed", dateFormat.format(Date(app.firstInstallTime)))
                    InfoDivider()
                    InfoRow("Updated", dateFormat.format(Date(app.lastUpdateTime)))
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile(
                    "Open app",
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { launchIntent?.let { safeStart(context, it) } },
                    modifier = Modifier.weight(1f),
                    enabled = launchIntent != null,
                )
                ActionTile(
                    "App info",
                    Icons.Rounded.Info,
                    onClick = {
                        safeStart(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                ActionTile("Store", Icons.Rounded.Shop, onClick = { openStore(context, app.packageName) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Split app format: APK or APKS
// -------------------------------------------------------------------------------------------------

@Composable
private fun FormatPicker(selected: SplitMode, onSelect: (SplitMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Text(
            "SAVE AS",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormatTile(
                title = "APK",
                subtitle = "One file that installs anywhere",
                selected = selected == SplitMode.MERGE,
                onClick = { onSelect(SplitMode.MERGE) },
                modifier = Modifier.weight(1f),
            )
            FormatTile(
                title = "APKS",
                subtitle = "Original parts, original signature",
                selected = selected == SplitMode.BUNDLE,
                onClick = { onSelect(SplitMode.BUNDLE) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        AnimatedContent(selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "formatNote") { mode ->
            if (mode == SplitMode.BUNDLE) {
                FormatNote(
                    Icons.Rounded.Info,
                    colors.primary,
                    "No extra app needed: install it with APK Extractor from the Install tab, or by sharing the file to it.",
                )
            } else {
                FormatNote(
                    Icons.Rounded.WarningAmber,
                    colors.error,
                    "Some apps may not open once merged into a single APK. If that happens, save as APKS instead.",
                )
            }
        }
    }
}

@Composable
private fun FormatTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val bg by animateColorAsState(
        when {
            selected -> colors.primaryContainer
            dark -> colors.surfaceContainerHigh
            else -> colors.surfaceContainerLowest
        },
        label = "tileBg",
    )
    val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "tileBorder")
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(bg)
            .border(if (selected) 1.5.dp else 1.dp, border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            RadioDot(selected)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.5.dp, colors.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun FormatNote(icon: ImageVector, tint: Color, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// -------------------------------------------------------------------------------------------------
// Extraction result
// -------------------------------------------------------------------------------------------------

@Composable
fun ExtractResultSheet(
    result: UiEvent.Extracted,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val ok = result.saved.isNotEmpty()
    Sheet(onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (ok) colors.primary else colors.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (ok) colors.onPrimary else colors.onErrorContainer,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Eyebrow(if (ok) "Extraction complete" else "Extraction failed")
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !ok -> if (result.failures.size == 1) "Couldn't save ${result.failures[0].first}" else "Couldn't save apps"
                    result.saved.size == 1 -> "${result.saved[0].label} is saved"
                    else -> "${result.saved.size} apps saved"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (ok) "In ${result.location}" else result.failures.firstOrNull()?.second.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            if (ok) {
                Spacer(Modifier.height(18.dp))
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        result.saved.forEachIndexed { i, item ->
                            if (i > 0) InfoDivider()
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(item.packageName, item.iconKey, 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    item.file.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            // Bundles (.apks) need a split installer — APK Extractor is one.
            if (ok && result.saved.any { it.file.displayName.endsWith(".apks", ignoreCase = true) }) {
                Spacer(Modifier.height(12.dp))
                FormatNote(
                    Icons.Rounded.Info,
                    colors.primary,
                    "To install the .apks bundle, open the Install tab here, or share the file to APK Extractor.",
                )
            }

            if (ok && result.failures.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${result.failures.size} couldn't be saved: " + result.failures.joinToString { it.first },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }

            Spacer(Modifier.height(22.dp))
            if (ok) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Open folder", onClick = onOpenFolder, modifier = Modifier.weight(1f))
                    TonalIconButton(Icons.Rounded.Share, "Share", onClick = onShare, size = 56.dp)
                }
            } else {
                SecondaryButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------------------------------

private fun safeStart(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Can't open this", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "Can't open this", Toast.LENGTH_SHORT).show()
    }
}

private fun openStore(context: Context, pkg: String) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
    try {
        context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        safeStart(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Package name", text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
