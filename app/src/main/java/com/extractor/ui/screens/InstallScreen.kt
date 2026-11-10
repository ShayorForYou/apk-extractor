package com.extractor.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.extractor.data.HistoryEntry
import com.extractor.data.PackageToInstall
import com.extractor.ui.InstallState
import com.extractor.ui.InstallViewModel
import com.extractor.ui.MainViewModel
import com.extractor.ui.components.AppIcon
import com.extractor.ui.components.Eyebrow
import com.extractor.ui.components.Panel
import com.extractor.ui.components.PrimaryButton
import com.extractor.ui.components.ScreenHeader
import com.extractor.ui.components.SecondaryButton
import com.extractor.ui.components.pressScale
import com.extractor.ui.components.relativeTime
import com.extractor.ui.components.rememberFileSize
import com.extractor.ui.components.versionLabel
import com.extractor.ui.components.FormatTag
import com.extractor.ui.components.packageFormat

// -------------------------------------------------------------------------------------------------
// Install tab
// -------------------------------------------------------------------------------------------------

@Composable
fun InstallScreen(
    vm: MainViewModel,
    installVm: InstallViewModel,
    contentPadding: PaddingValues,
    onPickFile: () -> Unit,
) {
    val history by vm.history.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            ScreenHeader(title = "Install", subtitle = "APK and split APK files")
        }
        item(key = "pick") { PickFileCard(onPickFile) }
        if (history.isNotEmpty()) {
            item(key = "backups") {
                Text(
                    "YOUR BACKUPS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, top = 28.dp, bottom = 10.dp),
                )
            }
            items(history.take(MAX_BACKUPS), key = { it.id }) { entry ->
                BackupRow(entry, onInstall = { installVm.open(entry) }, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

private const val MAX_BACKUPS = 10

/** The main call to action: a dashed "drop zone" card with a single button. */
@Composable
private fun PickFileCard(onPick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dash = colors.primary.copy(alpha = 0.45f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerLowest)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("Install from a file", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            "Pick an app file from your phone. Split apps are installed in one go.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        PrimaryButton("Choose file", onClick = onPick, modifier = Modifier.fillMaxWidth(0.8f))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(".apk", ".apks", ".xapk", ".apkm").forEach { FormatChip(it) }
        }
    }
}

@Composable
private fun FormatChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun BackupRow(entry: HistoryEntry, onInstall: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Panel(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), border = null, onClick = onInstall) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(entry.packageName, "${entry.packageName}@history", 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val size = rememberFileSize(entry.sizeBytes)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (entry.sizeBytes > 0) "${versionLabel(entry.versionName).take(16)}  ·  $size  ·  ${relativeTime(entry.time)}"
                        else relativeTime(entry.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    packageFormat(entry.fileName)?.let {
                        Spacer(Modifier.width(8.dp))
                        FormatTag(it)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            SmallPill("Install", onInstall)
        }
    }
}

@Composable
private fun SmallPill(text: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = colors.primary,
        modifier = Modifier
            .pressScale(source)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(source, ripple(color = colors.primary), role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

// -------------------------------------------------------------------------------------------------
// Install sheet — one fixed layout from "reading" to "open": icon, name, a status line and one
// button. Only the words change and the button itself fills up as progress, so nothing jumps.
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallSheet(state: InstallState, canInstall: Boolean, vm: InstallViewModel, onPickFile: () -> Unit) {
    val busy = state is InstallState.Installing
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || vm.state.value !is InstallState.Installing },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) vm.dismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        val ui = describe(state, canInstall, vm, onPickFile)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SheetIcon(state)
            Spacer(Modifier.height(16.dp))
            AnimatedContent(ui.title to ui.subtitle, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") { (title, subtitle) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (title == null) {
                        Placeholder(width = 150.dp, height = 22.dp)
                        Spacer(Modifier.height(10.dp))
                        Placeholder(width = 100.dp, height = 14.dp)
                    } else {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            StatusLine(ui.status, ui.tone)
            Spacer(Modifier.height(24.dp))
            MorphButton(ui.button, ui.onClick, ui.progress, ui.busy)
            // Fixed-height footer so the sheet never changes size between states.
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                val footer = ui.footer
                if (footer != null) {
                    TextButton(onClick = footer.second) {
                        Text(footer.first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (ui.caption != null) {
                    Text(
                        ui.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private enum class Tone { Neutral, Accent, Success, Warning }

/** Everything the sheet shows for one state. */
private class SheetUi(
    val title: String?,
    val subtitle: String?,
    val status: String,
    val tone: Tone,
    val button: String,
    val onClick: (() -> Unit)?,
    val progress: Float? = null,
    val busy: Boolean = false,
    val caption: String? = null,
    val footer: Pair<String, () -> Unit>? = null,
)

@Composable
private fun describe(state: InstallState, canInstall: Boolean, vm: InstallViewModel, onPickFile: () -> Unit): SheetUi {
    fun subtitleOf(pkg: PackageToInstall, size: String) = "${versionLabel(pkg.versionName).take(20)}  ·  $size"
    return when (state) {
        InstallState.Idle, InstallState.Reading -> SheetUi(
            title = null, subtitle = null,
            status = "Reading file", tone = Tone.Neutral,
            button = "Reading…", onClick = null, busy = true,
        )
        is InstallState.Ready -> {
            val pkg = state.pkg
            val size = rememberFileSize(pkg.totalBytes)
            val parts = if (pkg.isBundle) "  ·  ${pkg.files.size} parts" else ""
            when {
                pkg.needsNewerAndroid -> SheetUi(
                    pkg.label, subtitleOf(pkg, size),
                    "Needs Android ${androidName(pkg.minSdk)} or newer", Tone.Warning,
                    "Close", vm::dismiss,
                )
                pkg.isInstalled && !pkg.signatureMatches -> SheetUi(
                    pkg.label, subtitleOf(pkg, size),
                    "Signed differently from the installed app", Tone.Warning,
                    "Uninstall current version", vm::uninstallInstalled,
                    caption = "Then come back to install this one.",
                )
                pkg.isDowngrade -> SheetUi(
                    pkg.label, subtitleOf(pkg, size),
                    "Older than installed ${versionLabel(pkg.installedVersionName.orEmpty()).take(14)}", Tone.Warning,
                    "Uninstall current version", vm::uninstallInstalled,
                    caption = "Then come back to install this one.",
                )
                else -> {
                    val (status, action) = when {
                        !pkg.isInstalled -> "New app$parts" to "Install"
                        pkg.versionCode == pkg.installedVersionCode -> "Already installed$parts" to "Reinstall"
                        else -> "Update from ${versionLabel(pkg.installedVersionName.orEmpty()).take(14)}$parts" to "Update"
                    }
                    SheetUi(
                        pkg.label, subtitleOf(pkg, size), status, Tone.Accent, action, vm::install,
                        caption = if (canInstall) null else "Android will ask you to allow installs once.",
                    )
                }
            }
        }
        is InstallState.Installing -> {
            val pkg = state.pkg
            val size = rememberFileSize(pkg.totalBytes)
            if (state.waitingForUser) {
                SheetUi(
                    pkg.label, subtitleOf(pkg, size),
                    "Waiting for your confirmation", Tone.Neutral,
                    "Confirm install", vm::showPromptAgain,
                    footer = "Cancel" to vm::cancelInstall,
                )
            } else {
                val p = state.progress
                SheetUi(
                    pkg.label, subtitleOf(pkg, size),
                    "Installing", Tone.Accent,
                    if (p != null) "Installing  ${(p * 100).toInt()}%" else "Installing…", null,
                    progress = p, busy = true,
                )
            }
        }
        is InstallState.Done -> {
            val pkg = state.pkg
            val size = rememberFileSize(pkg.totalBytes)
            SheetUi(
                pkg.label, subtitleOf(pkg, size),
                "Installed", Tone.Success,
                if (state.canOpen) "Open" else "Done",
                if (state.canOpen) vm::openInstalled else vm::dismiss,
                footer = if (state.canOpen) "Done" to vm::dismiss else null,
            )
        }
        is InstallState.Failed -> {
            val pkg = state.pkg
            if (pkg != null) {
                SheetUi(
                    pkg.label, subtitleOf(pkg, rememberFileSize(pkg.totalBytes)),
                    state.message, Tone.Warning,
                    "Try again", vm::retry,
                    footer = "Close" to vm::dismiss,
                )
            } else {
                SheetUi(
                    "Couldn't read this file", "Make sure it's an APK or APK bundle",
                    state.message, Tone.Warning,
                    "Choose another file", { vm.dismiss(); onPickFile() },
                    footer = "Close" to vm::dismiss,
                )
            }
        }
    }
}

/** App icon, with a small badge for the final state. */
@Composable
private fun SheetIcon(state: InstallState) {
    val colors = MaterialTheme.colorScheme
    val pkg = when (state) {
        is InstallState.Ready -> state.pkg
        is InstallState.Installing -> state.pkg
        is InstallState.Done -> state.pkg
        is InstallState.Failed -> state.pkg
        else -> null
    }
    Box(Modifier.size(80.dp)) {
        val icon = pkg?.icon
        when {
            icon != null -> Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            state is InstallState.Failed -> Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(colors.errorContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.onErrorContainer, modifier = Modifier.size(34.dp)) }
            pkg != null -> Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(34.dp)) }
            else -> Placeholder(80.dp, 80.dp, RoundedCornerShape(24.dp))
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state is InstallState.Done,
            enter = androidx.compose.animation.scaleIn() + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerLow)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
private fun StatusLine(text: String, tone: Tone) {
    val colors = MaterialTheme.colorScheme
    val (dot, fg) = when (tone) {
        Tone.Neutral -> colors.outline to colors.onSurfaceVariant
        Tone.Accent -> colors.primary to colors.onSurface
        Tone.Success -> colors.primary to colors.primary
        Tone.Warning -> colors.error to colors.error
    }
    AnimatedContent(text to tone, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { (t, _) ->
        Row(
            Modifier
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(8.dp))
            Text(t, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The single action. While busy it becomes its own progress bar: a soft track that fills with the
 * accent (or a gliding band when the length of the step is unknown).
 */
@Composable
private fun MorphButton(label: String, onClick: (() -> Unit)?, progress: Float?, busy: Boolean) {
    val colors = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val container by androidx.compose.animation.animateColorAsState(
        if (busy) colors.primaryContainer else colors.primary, label = "btnBg",
    )
    val content by androidx.compose.animation.animateColorAsState(
        if (busy) colors.onPrimaryContainer else colors.onPrimary, label = "btnFg",
    )
    val fill by androidx.compose.animation.core.animateFloatAsState(progress ?: 0f, label = "fill")
    val sweep = androidx.compose.animation.core.rememberInfiniteTransition(label = "sweep")
    val band by sweep.animateFloat(
        initialValue = -0.4f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ),
        label = "band",
    )
    val bandColor = colors.primary.copy(alpha = 0.22f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(source)
            .clip(CircleShape)
            .background(container)
            .drawBehind {
                if (!busy) return@drawBehind
                if (progress != null) {
                    drawRect(bandColor, size = Size(size.width * fill, size.height))
                } else {
                    drawRect(bandColor, topLeft = Offset(size.width * band, 0f), size = Size(size.width * 0.4f, size.height))
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(source, ripple(color = content), role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "btnText") { text ->
            Text(text, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1)
        }
    }
}

@Composable
private fun Placeholder(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "ph")
    val alpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(700),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "phAlpha",
    )
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    )
}

private fun androidName(sdk: Int): String = when (sdk) {
    21 -> "5.0"; 22 -> "5.1"; 23 -> "6"; 24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"; 28 -> "9"
    29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; 37 -> "17"
    else -> if (sdk < 21) "4.x" else "API $sdk"
}
