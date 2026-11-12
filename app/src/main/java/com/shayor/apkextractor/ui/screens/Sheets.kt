package com.shayor.apkextractor.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.shayor.apkextractor.data.AppDetails
import com.shayor.apkextractor.data.AppDetailsLoader
import com.shayor.apkextractor.data.AppInfo
import com.shayor.apkextractor.data.SplitMode
import com.shayor.apkextractor.ui.UiEvent
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.CellDivider
import com.shayor.apkextractor.ui.components.CloseButton
import com.shayor.apkextractor.ui.components.FormatTag
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.InsetGroup
import com.shayor.apkextractor.ui.components.IosSheet
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.SecondaryButton
import com.shayor.apkextractor.ui.components.SectionHeader
import com.shayor.apkextractor.ui.components.SegmentLabel
import com.shayor.apkextractor.ui.components.ShareButton
import com.shayor.apkextractor.ui.components.SegmentedControl
import com.shayor.apkextractor.ui.components.SwitchCell
import com.shayor.apkextractor.ui.components.TonalIconButton
import com.shayor.apkextractor.ui.components.cellColor
import com.shayor.apkextractor.ui.components.packageFormat
import com.shayor.apkextractor.ui.components.pressScale
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.versionLabel
import com.shayor.apkextractor.ui.theme.iosColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    IosSheet(onDismiss) { content() }
}

/**
 * [verticalScroll] for content inside a [Sheet], with the behaviour people expect from sheets.
 *
 * Out of the box, a downward swipe in scrolled content scrolls it to the top and then hands the
 * rest of the drag (and of the fling's velocity) to the sheet, which slides closed in the same
 * motion. Here, a swipe that starts while the content is scrolled stops at the top, and only a new
 * swipe starting from the top moves the sheet.
 */
@Composable
private fun Modifier.sheetScroll(state: ScrollState): Modifier {
    val guard = remember(state) { SheetScrollGuard() }
    return this
        .pointerInput(guard) {
            awaitEachGesture {
                // Initial pass: seen before the scrollable reacts, without consuming anything.
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                guard.startedScrolled = state.value > 0
            }
        }
        .nestedScroll(guard)
        .verticalScroll(state)
}

private class SheetScrollGuard : NestedScrollConnection {
    /** Whether the current (or just-released, still flinging) gesture began away from the top. */
    var startedScrolled = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (startedScrolled && available.y > 0f) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (startedScrolled && available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
}

// -------------------------------------------------------------------------------------------------
// App details
// -------------------------------------------------------------------------------------------------

@Composable
fun AppDetailsSheet(
    app: AppInfo,
    splitMode: SplitMode,
    autoBackup: Boolean,
    saveLocation: String,
    onDismiss: () -> Unit,
    onExtract: (SplitMode) -> Unit,
    onShare: (SplitMode) -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val p = iosColors
    // Loaded when the sheet opens: too costly to read for every app in the list.
    val details by produceState<AppDetails?>(null, app.packageName, app.lastUpdateTime) {
        value = AppDetailsLoader.load(context, app)
    }
    // Split apps can be saved either way; start from the default chosen in Settings.
    var format by rememberSaveable(app.packageName) {
        mutableStateOf(if (splitMode == SplitMode.BUNDLE) SplitMode.BUNDLE else SplitMode.MERGE)
    }
    val mode = if (app.isSplit) format else splitMode
    val launchIntent = remember(app.packageName, app.lastUpdateTime) {
        context.packageManager.getLaunchIntentForPackage(app.packageName)
    }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Sheet(onDismiss) {
        Column(
            Modifier
                .sheetScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            // App Store–style header: big icon, name, version, and the main action under them.
            Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
                AppIcon(app.packageName, app.iconKey, 84.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${versionLabel(app).take(20)}  ·  ${rememberFileSize(app.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.secondaryLabel,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(6.dp))
                    FormatTag(
                        packageFormat(app),
                        detail = if (app.isSplit) "${app.splitSourceDirs.size + 1} PARTS" else null,
                    )
                }
                CloseButton(onDismiss)
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(
                    if (app.isSplit && format == SplitMode.BUNDLE) "Extract APKS" else "Extract APK",
                    onClick = { onExtract(mode) },
                    icon = Icons.Rounded.Download,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                ShareButton(app.isSplit, onShare = { chosen -> onShare(chosen ?: mode) }, size = 50.dp)
            }

            // Like the action buttons on an iOS contact card.
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    "Open",
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { launchIntent?.let { safeStart(context, it) } },
                    enabled = launchIntent != null,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    "App Info",
                    Icons.Rounded.Info,
                    onClick = {
                        safeStart(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                QuickAction("Store", Icons.Rounded.Shop, onClick = { openStore(context, app.packageName) }, modifier = Modifier.weight(1f))
            }

            if (app.isSplit) FormatPicker(format, onSelect = { format = it })

            SectionHeader("Backup")
            InsetGroup {
                SwitchCell(
                    "Auto-Backup Updates",
                    autoBackup,
                    onAutoBackupChange,
                    icon = { IconTile(Icons.Rounded.Autorenew, p.green) },
                    subtitle = "Save each new version to $saveLocation",
                )
            }

            SectionHeader("Information")
            InsetGroup {
                InfoRow("Package", app.packageName, onClick = { copyToClipboard(context, "Package name", app.packageName) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = p.tertiaryLabel, modifier = Modifier.size(15.dp))
                }
                CellDivider()
                InfoRow("Version Code", app.versionCode.toString())
                CellDivider()
                InfoRow("Android", "${androidName(app.minSdk)}+  ·  targets ${androidName(app.targetSdk)}")
                CellDivider()
                InfoRow("Installed", dateFormat.format(Date(app.firstInstallTime)))
                CellDivider()
                InfoRow("Updated", dateFormat.format(Date(app.lastUpdateTime)))
                CellDivider()
                InfoRow("Installed From", details?.let { it.installer ?: "Unknown" } ?: "…")
                CellDivider()
                val signatures = details?.signatures.orEmpty()
                val signature = signatures.firstOrNull()
                InfoRow(
                    "Signature",
                    when {
                        details == null -> "…"
                        signature == null -> "Unknown"
                        else -> "SHA-256 ${signature.take(11)}…${signature.takeLast(5)}"
                    },
                    onClick = if (signature != null) {
                        { copyToClipboard(context, "Signature", signatures.joinToString("\n")) }
                    } else {
                        null
                    },
                ) {
                    if (signature != null) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = p.tertiaryLabel, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

/** An accent glyph over a small label on a rounded tile, like the buttons on an iOS contact card. */
@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    val source = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(source, 0.94f)
            .clip(RoundedCornerShape(16.dp))
            .background(cellColor())
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1)
    }
}

/** A row of the information table: the label on the left, the value in grey on the right. */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    maxLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth()
    Row(
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = iosColors.secondaryLabel,
            textAlign = TextAlign.End,
            maxLines = maxLines,
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
    val p = iosColors
    Column {
        SectionHeader("Save As")
        SegmentedControl(
            options = listOf(SplitMode.MERGE, SplitMode.BUNDLE),
            selected = selected,
            onSelect = onSelect,
            height = 36.dp,
        ) { mode, active -> SegmentLabel(if (mode == SplitMode.MERGE) "Single APK" else "APKS Bundle", active) }
        Spacer(Modifier.height(8.dp))
        AnimatedContent(selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "formatNote") { mode ->
            if (mode == SplitMode.BUNDLE) {
                FormatNote(
                    Icons.Rounded.Info,
                    MaterialTheme.colorScheme.primary,
                    "Keeps the original parts and signature. Install it with APK Extractor from the Install tab, or by sharing the file to it.",
                )
            } else {
                FormatNote(
                    Icons.Rounded.WarningAmber,
                    p.orange,
                    "One file that installs anywhere. Some apps may not open once merged; if so, save as APKS instead.",
                )
            }
        }
    }
}

/** Footnote under a control, with a small leading symbol. */
@Composable
private fun FormatNote(icon: ImageVector, tint: Color, text: String) {
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = iosColors.secondaryLabel)
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
    val p = iosColors
    val ok = result.saved.isNotEmpty()
    Sheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The badge pops in with a little overshoot, like iOS completion checkmarks.
            val shown = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = shown,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.4f) + fadeIn(),
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (ok) p.green else p.red),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ok) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    !ok -> if (result.failures.size == 1) "Couldn't Save ${result.failures[0].first}" else "Couldn't Save Apps"
                    result.saved.size == 1 -> "${result.saved[0].label} Saved"
                    else -> "${result.saved.size} Apps Saved"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (ok) "In ${result.location}" else result.failures.firstOrNull()?.second.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
            )

            if (ok) {
                Spacer(Modifier.height(18.dp))
                InsetGroup {
                    Column(Modifier.heightIn(max = 240.dp).sheetScroll(rememberScrollState())) {
                        result.saved.forEachIndexed { i, item ->
                            if (i > 0) CellDivider(62.dp)
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.height(10.dp))
                FormatNote(
                    Icons.Rounded.Info,
                    MaterialTheme.colorScheme.primary,
                    "To install the .apks bundle, open the Install tab here, or share the file to APK Extractor.",
                )
            }

            if (ok && result.failures.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "${result.failures.size} couldn't be saved: " + result.failures.joinToString { it.first },
                    style = MaterialTheme.typography.bodySmall,
                    color = p.red,
                )
            }

            Spacer(Modifier.height(22.dp))
            if (ok) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrimaryButton("Open Folder", onClick = onOpenFolder, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    TonalIconButton(Icons.Rounded.Share, "Share", onClick = onShare, size = 50.dp)
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

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
