package com.shayor.apkextractor.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import com.shayor.apkextractor.ui.components.Cell
import com.shayor.apkextractor.ui.components.CellDivider
import com.shayor.apkextractor.ui.components.InsetGroup
import com.shayor.apkextractor.ui.components.SectionFooter
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.shayor.apkextractor.data.HistoryEntry
import com.shayor.apkextractor.data.PackageToInstall
import com.shayor.apkextractor.ui.InstallState
import com.shayor.apkextractor.ui.InstallViewModel
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.GlassTopBar
import com.shayor.apkextractor.ui.components.GroupShape
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.IosSheet
import com.shayor.apkextractor.ui.components.PillButton
import com.shayor.apkextractor.ui.components.SectionHeader
import com.shayor.apkextractor.ui.components.cellColor
import com.shayor.apkextractor.ui.components.glassSource
import com.shayor.apkextractor.ui.components.groupItemShape
import com.shayor.apkextractor.ui.components.scrolledUnderBar
import com.shayor.apkextractor.ui.components.topBarHeight
import com.shayor.apkextractor.ui.theme.iosColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.SecondaryButton
import com.shayor.apkextractor.ui.components.pressScale
import com.shayor.apkextractor.ui.components.relativeTime
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.versionLabel
import com.shayor.apkextractor.ui.components.FormatTag
import com.shayor.apkextractor.ui.components.packageFormat

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
    val canInstall by installVm.canInstall.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledUnderBar()
    val backups = history.take(MAX_BACKUPS)
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarHeight() + 8.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            modifier = Modifier
                .fillMaxSize()
                .glassSource(),
        ) {
            item(key = "pick") {
                PickFileCard(onPickFile)
                if (!canInstall) SectionFooter("Android asks you to allow installs from APK Extractor the first time.")
            }
            if (backups.isNotEmpty()) {
                item(key = "backups") { SectionHeader("Recent Backups") }
                itemsIndexed(backups, key = { _, it -> it.id }) { i, entry ->
                    BackupRow(
                        entry,
                        first = i == 0,
                        last = i == backups.lastIndex,
                        onInstall = { installVm.open(entry) },
                    )
                }
                item(key = "backupsFooter") {
                    SectionFooter("Your latest backups. Every saved file is in History.")
                }
            }
        }
        GlassTopBar("Install", collapsed = collapsed)
    }
}

private const val MAX_BACKUPS = 10

/**
 * The main call to action, as one polished iOS card: a glossy accent icon with a soft glow, a title,
 * a line of help and a capsule button; a hairline footer lists the formats it opens.
 */
@Composable
private fun PickFileCard(onPick: () -> Unit) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val gradient = remember(accent) {
        Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.22f), lerp(accent, Color.Black, 0.10f)))
    }
    val tile = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(cellColor()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .dropShadow(tile, Shadow(radius = 16.dp, color = accent, offset = DpOffset(0.dp, 6.dp), alpha = 0.32f))
                    .clip(tile)
                    .background(gradient)
                    .border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Install from a File", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pick an app file from your phone.\nSplit apps are installed in one go.",
                style = MaterialTheme.typography.bodyMedium,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Choose File", onClick = onPick, icon = Icons.Rounded.FolderOpen, modifier = Modifier.fillMaxWidth())
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(p.separator))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Supports", style = MaterialTheme.typography.bodySmall, color = p.secondaryLabel)
            Spacer(Modifier.width(10.dp))
            listOf("APK", "APKS", "XAPK", "APKM").forEachIndexed { i, f ->
                if (i > 0) Spacer(Modifier.width(5.dp))
                Text(
                    f,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = p.secondaryLabel,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(p.tertiaryFill)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** A backup in an inset grouped list, with an App Store–style "Install" pill. */
@Composable
private fun BackupRow(entry: HistoryEntry, first: Boolean, last: Boolean, onInstall: () -> Unit) {
    val p = iosColors
    val separator = p.separator
    val fileSize = rememberFileSize(entry.sizeBytes)
    val bundle = packageFormat(entry.fileName)?.takeIf { it.accent }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(groupItemShape(first, last))
            .background(cellColor())
            .drawBehind {
                if (!first) {
                    val x = 72.dp.toPx()
                    drawRect(separator, topLeft = Offset(x, 0f), size = Size(size.width - x, 0.5.dp.toPx()))
                }
            }
            .clickable(onClick = onInstall)
            .padding(start = 16.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.packageName, "${entry.packageName}@history", 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(versionLabel(entry.versionName).take(14), fileSize.takeIf { entry.sizeBytes > 0 }).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = p.secondaryLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(relativeTime(entry.time), style = MaterialTheme.typography.labelMedium, color = p.tertiaryLabel, maxLines = 1)
                if (bundle != null) {
                    Spacer(Modifier.width(6.dp))
                    FormatTag(bundle)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        PillButton("Install", onClick = onInstall)
    }
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
    IosSheet(onDismiss = { if (!busy) vm.dismiss() }, sheetState = sheetState) {
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
            // Fixed heights from here down, so the sheet never resizes between states (a resize
            // during its slide-in made it stop and jump).
            AnimatedContent(
                ui.title to ui.subtitle,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.Center,
                label = "title",
                modifier = Modifier.height(84.dp),
            ) { (title, subtitle) ->
                Column(
                    Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
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
            Spacer(Modifier.height(10.dp))
            Box(Modifier.height(58.dp), contentAlignment = Alignment.Center) { StatusLine(ui.status, ui.tone) }
            Spacer(Modifier.height(16.dp))
            MorphButton(ui.button, ui.onClick, ui.progress, ui.busy)
            // Fixed-height footer so the sheet never changes size between states.
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                val footer = ui.footer
                if (footer != null) {
                    Text(
                        footer.first,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = footer.second)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
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
                    .background(iosColors.sheet)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(iosColors.green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
private fun StatusLine(text: String, tone: Tone) {
    val colors = MaterialTheme.colorScheme
    val p = iosColors
    val (dot, fg) = when (tone) {
        Tone.Neutral -> p.gray to p.secondaryLabel
        Tone.Accent -> colors.primary to p.label
        Tone.Success -> p.green to p.green
        Tone.Warning -> p.orange to p.orange
    }
    AnimatedContent(text to tone, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") { (t, _) ->
        Row(
            Modifier
                .clip(CircleShape)
                .background(p.tertiaryFill)
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
            .height(54.dp)
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
                    Modifier.clickable(source, indication = null, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "btnText") { text ->
            Text(text, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1)
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
            .background(iosColors.tertiaryFill),
    )
}

internal fun androidName(sdk: Int): String = when (sdk) {
    21 -> "5.0"; 22 -> "5.1"; 23 -> "6"; 24 -> "7.0"; 25 -> "7.1"; 26 -> "8.0"; 27 -> "8.1"; 28 -> "9"
    29 -> "10"; 30 -> "11"; 31 -> "12"; 32 -> "12L"; 33 -> "13"; 34 -> "14"; 35 -> "15"; 36 -> "16"; 37 -> "17"
    else -> if (sdk < 21) "4.x" else "API $sdk"
}
