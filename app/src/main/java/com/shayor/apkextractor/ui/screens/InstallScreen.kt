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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.launch
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.GlassTopBar
import com.shayor.apkextractor.ui.components.GroupShape
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.IosSheet
import com.shayor.apkextractor.ui.components.rememberIosSheetState
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
    val missingIds by vm.missingIds.collectAsStateWithLifecycle()
    val canInstall by installVm.canInstall.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val collapsed by listState.scrolledUnderBar()
    val scope = rememberCoroutineScope()
    // Files that are gone aren't offered here; they're flagged in History.
    val backups = history.filter { it.id !in missingIds }.take(MAX_BACKUPS)
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
                        // Re-checked on tap: the file may have gone since the list was last checked.
                        onInstall = { scope.launch { if (vm.ensureReadable(entry)) installVm.open(entry) } },
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
    val sheetState = rememberIosSheetState(
        confirmValueChange = { it != SheetValue.Hidden || vm.state.value !is InstallState.Installing },
    )
    IosSheet(onDismiss = { if (!busy) vm.dismiss() }, sheetState = sheetState) {
        val ui = describe(state, canInstall, vm, onPickFile)
        // The same height in every state, so the sheet never resizes (a resize during its slide-in
        // made it stop and jump): the header and the card are each one fixed-height row.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 2.dp, bottom = 12.dp),
        ) {
            SheetHeader(state, ui.title, ui.subtitle)
            Spacer(Modifier.height(18.dp))
            DetailCard(ui.detail)
            Spacer(Modifier.height(16.dp))
            MorphButton(ui.button, ui.onClick, ui.progress, ui.busy)
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                val footer = ui.footer
                if (footer != null) {
                    Text(
                        footer.first,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = footer.second)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                } else if (ui.caption != null) {
                    Text(
                        ui.caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Where an app hidden from us usually lives, and what to do about it. */
private const val HiddenCopyHint = "Remove it from Secure Folder or the other user first."

private enum class Tone { Neutral, Accent, Success, Warning }

/** The middle of the sheet. */
private sealed interface Detail {
    data class Status(val text: String, val tone: Tone) : Detail
    /** A new app: version, size and the Android it needs. */
    data class Facts(val pkg: PackageToInstall) : Detail
    /** Already on the phone: installed version → this file's. */
    data class Compare(val pkg: PackageToInstall) : Detail
}

/** Facts for a new app, the comparison for one that is already installed. */
private fun overview(pkg: PackageToInstall): Detail =
    if (pkg.isInstalled && !pkg.installedElsewhere) Detail.Compare(pkg) else Detail.Facts(pkg)

/** Everything the sheet shows for one state. */
private class SheetUi(
    val title: String?,
    val subtitle: String?,
    val detail: Detail,
    val button: String,
    val onClick: (() -> Unit)?,
    val progress: Float? = null,
    val busy: Boolean = false,
    val caption: String? = null,
    val footer: Pair<String, () -> Unit>? = null,
)

@Composable
private fun describe(state: InstallState, canInstall: Boolean, vm: InstallViewModel, onPickFile: () -> Unit): SheetUi {
    fun warn(text: String) = Detail.Status(text, Tone.Warning)
    return when (state) {
        InstallState.Idle, InstallState.Reading -> SheetUi(
            title = null, subtitle = null,
            Detail.Status("Reading file", Tone.Neutral),
            button = "Reading…", onClick = null, busy = true,
        )
        is InstallState.Ready -> {
            val pkg = state.pkg
            when {
                pkg.needsNewerAndroid -> SheetUi(
                    pkg.label, pkg.packageName,
                    warn("Needs Android ${androidName(pkg.minSdk)} or newer"),
                    "Close", vm::dismiss,
                )
                // Not installed for this user, but Android still keeps a copy (another profile such
                // as Secure Folder or Dual Messenger, or data kept on uninstall) that blocks it.
                pkg.installedElsewhere && (!pkg.signatureMatches || pkg.isDowngrade) -> SheetUi(
                    pkg.label, pkg.packageName,
                    warn(if (!pkg.signatureMatches) "Blocked by a hidden copy" else "A newer copy is on this phone"),
                    // Android's uninstall prompt can't reach a copy in another user or profile, so
                    // say where to remove it instead, then let them try again here.
                    "Try again", vm::install,
                    caption = HiddenCopyHint,
                )
                pkg.isInstalled && !pkg.signatureMatches -> SheetUi(
                    pkg.label, pkg.packageName,
                    warn("Different signature"),
                    "Uninstall current version", vm::uninstallInstalled,
                    caption = "Then install this one.",
                )
                // Older than installed: the comparison says it all; Android won't downgrade.
                pkg.isDowngrade -> SheetUi(
                    pkg.label, pkg.packageName,
                    overview(pkg),
                    "Uninstall current version", vm::uninstallInstalled,
                    caption = "Android can't downgrade apps.",
                )
                else -> SheetUi(
                    pkg.label, pkg.packageName,
                    overview(pkg),
                    when {
                        !pkg.isInstalled || pkg.installedElsewhere -> "Install"
                        pkg.versionCode == pkg.installedVersionCode -> "Reinstall"
                        else -> "Update"
                    },
                    vm::install,
                    caption = when {
                        !canInstall -> "You'll be asked to allow installs once."
                        pkg.hasObb -> "Includes ${rememberFileSize(pkg.obbBytes)} of game data"
                        else -> null
                    },
                )
            }
        }
        is InstallState.Installing -> {
            val pkg = state.pkg
            if (state.waitingForUser) {
                SheetUi(
                    pkg.label, pkg.packageName,
                    Detail.Status("Waiting for confirmation", Tone.Neutral),
                    "Confirm install", vm::showPromptAgain,
                    footer = "Cancel" to vm::cancelInstall,
                )
            } else {
                // The button shows the progress; the details stay as they were.
                val p = state.progress
                SheetUi(
                    pkg.label, pkg.packageName,
                    overview(pkg),
                    if (p != null) "Installing  ${(p * 100).toInt()}%" else "Installing…", null,
                    progress = p, busy = true,
                )
            }
        }
        is InstallState.Done -> {
            val pkg = state.pkg
            SheetUi(
                pkg.label, pkg.packageName,
                Detail.Status(
                    when {
                        // pkg still describes what was on the phone before this install.
                        !pkg.isInstalled || pkg.installedElsewhere -> "Installed"
                        pkg.versionCode == pkg.installedVersionCode -> "Reinstalled"
                        else -> "Updated to ${versionLabel(pkg.versionName).take(18)}"
                    },
                    Tone.Success,
                ),
                if (state.canOpen) "Open" else "Done",
                if (state.canOpen) vm::openInstalled else vm::dismiss,
                footer = if (state.canOpen) "Done" to vm::dismiss else null,
            )
        }
        is InstallState.Failed -> {
            val pkg = state.pkg
            if (pkg != null && state.conflict) {
                val visible = pkg.isInstalled && !pkg.installedElsewhere
                SheetUi(
                    pkg.label, pkg.packageName,
                    warn(state.message),
                    // Installed normally: Android's uninstall prompt removes it. Otherwise the copy
                    // is in another user or profile, out of our reach: explain where to remove it.
                    if (visible) "Uninstall current version" else "Try again",
                    if (visible) vm::uninstallInstalled else vm::retry,
                    caption = if (visible) "Then install this one." else HiddenCopyHint,
                )
            } else if (pkg != null) {
                SheetUi(
                    pkg.label, pkg.packageName,
                    warn(state.message),
                    "Try again", vm::retry,
                    footer = "Close" to vm::dismiss,
                )
            } else {
                SheetUi(
                    "Couldn't read this file", "Make sure it's an APK or APK bundle",
                    warn(state.message),
                    "Choose another file", { vm.dismiss(); onPickFile() },
                    footer = "Close" to vm::dismiss,
                )
            }
        }
    }
}

/** Icon beside the name and package, like the header of an iOS install sheet. */
@Composable
private fun SheetHeader(state: InstallState, title: String?, subtitle: String?) {
    val p = iosColors
    val titleStyle = MaterialTheme.typography.titleLarge.copy(color = p.label, fontWeight = FontWeight.SemiBold)
    val subtitleStyle = MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SheetIcon(state)
        Spacer(Modifier.width(14.dp))
        // Reserves one title line and one package line, so the placeholder and text match.
        ReserveHeight(
            reserve = {
                Column {
                    Text("", style = titleStyle, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Text("", style = subtitleStyle, maxLines = 1)
                }
            },
        ) {
            AnimatedContent(
                title to subtitle,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.CenterStart,
                label = "title",
                modifier = Modifier.fillMaxSize(),
            ) { (t, sub) ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    if (t == null) {
                        Placeholder(width = 150.dp, height = 18.dp)
                        Spacer(Modifier.height(8.dp))
                        Placeholder(width = 104.dp, height = 12.dp)
                    } else {
                        FitText(t, titleStyle, minSize = 15.sp)
                        // An app without a name is titled by its package: don't repeat it.
                        if (sub != null && sub != t) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            sub,
                            style = subtitleStyle,
                            color = p.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        }
                    }
                }
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
    val shape = RoundedCornerShape(15.dp)
    Box(Modifier.size(IconSize)) {
        val icon = pkg?.icon
        when {
            icon != null -> Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            state is InstallState.Failed -> Box(
                Modifier.fillMaxSize().clip(shape).background(colors.errorContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.onErrorContainer, modifier = Modifier.size(26.dp)) }
            pkg != null -> Box(
                Modifier.fillMaxSize().clip(shape).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(26.dp)) }
            else -> Placeholder(IconSize, IconSize, shape)
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state is InstallState.Done,
            enter = androidx.compose.animation.scaleIn() + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 0.dp),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(iosColors.sheet)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(iosColors.green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
    }
}

private val IconSize = 56.dp

/**
 * Lays out [reserve] invisibly to take its size, and draws [content] over it. The area is then
 * exactly as tall as its tallest state at the current font size, and the same in every state.
 */
@Composable
private fun ReserveHeight(reserve: @Composable () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) { reserve() }
        Box(Modifier.matchParentSize()) { content() }
    }
}

/**
 * The one card in the middle of the sheet, the same size in every state: an app's facts, how it
 * compares with the installed version, or a status line.
 */
@Composable
private fun DetailCard(detail: Detail) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(cellColor()),
    ) {
        // An invisible row of facts gives the card its height (a label and a value, at any font size).
        Row(Modifier.alpha(0f).clearAndSetSemantics {}.padding(vertical = 12.dp)) { Fact("A", "1") }
        AnimatedContent(
            detail,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.Center,
            label = "detail",
            modifier = Modifier.matchParentSize(),
        ) { d ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (d) {
                    is Detail.Status -> StatusLine(d.text, d.tone)
                    is Detail.Facts -> FactsRow(d.pkg)
                    is Detail.Compare -> VersionCompare(d.pkg)
                }
            }
        }
    }
}

/** A new app at a glance, like the facts row on an App Store page: version, size, Android. */
@Composable
private fun FactsRow(pkg: PackageToInstall) {
    FactCells {
        // The version gets the most room: some are long ("26.43.30 (260400-1006944126)").
        Fact("Version", versionLabel(pkg.versionName), Modifier.weight(1.5f))
        FactDivider()
        Fact("Size", rememberFileSize(pkg.totalBytes), Modifier.weight(1f))
        FactDivider()
        Fact("Requires", "Android ${androidName(pkg.minSdk)}+", Modifier.weight(1f))
    }
}

@Composable
private fun FactCells(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) =
    Fact(label, AnnotatedString(value), modifier)

@Composable
private fun Fact(
    label: String,
    value: AnnotatedString,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
) {
    val p = iosColors
    Column(
        modifier
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = labelColor.takeOrElse { p.secondaryLabel },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        FitText(value, MaterialTheme.typography.titleSmall.copy(color = p.label, textAlign = TextAlign.Center))
    }
}

/** One line that shrinks (down to [minSize]) to fit whole rather than being cut off. */
@Composable
private fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, minSize: androidx.compose.ui.unit.TextUnit = 10.sp) =
    FitText(AnnotatedString(text), style, modifier, minSize)

@Composable
private fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minSize: androidx.compose.ui.unit.TextUnit = 10.sp,
) {
    BasicText(
        text,
        modifier = modifier,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis, // only if even the smallest size isn't enough
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

@Composable
private fun FactDivider() {
    Box(
        Modifier
            .fillMaxHeight(0.7f)
            .width(0.5.dp)
            .background(iosColors.separator),
    )
}

/**
 * Already installed: the installed version and this file's, side by side in the card. The label
 * over the new one names the change ("Minor update"), and only the part of the version that differs
 * is tinted (accent for an update, orange for an older version). The same version shows once.
 */
@Composable
private fun VersionCompare(pkg: PackageToInstall) {
    val p = iosColors
    val installedCode = pkg.installedVersionCode ?: return
    val fromName = versionLabel(pkg.installedVersionName.orEmpty().ifBlank { installedCode.toString() })
    val toName = versionLabel(pkg.versionName)
    if (pkg.versionCode == installedCode) {
        FactCells {
            Fact("Version", toName, Modifier.weight(1.5f))
            FactDivider()
            Fact("Size", rememberFileSize(pkg.totalBytes), Modifier.weight(1f))
            FactDivider()
            Fact("Installed", "Same", Modifier.weight(1f))
        }
        return
    }
    val newer = pkg.versionCode > installedCode
    val tint = if (newer) MaterialTheme.colorScheme.primary else p.orange
    val split = changedFrom(fromName, toName)
    val to = buildAnnotatedString {
        append(toName.take(split))
        withStyle(SpanStyle(color = tint)) { append(toName.drop(split)) }
    }
    FactCells {
        Fact("Installed", buildAnnotatedString { withStyle(SpanStyle(color = p.secondaryLabel)) { append(fromName) } }, Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "to",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Fact(changeCaption(fromName, toName, newer), to, Modifier.weight(1f), labelColor = tint)
    }
}

/** Where [to] starts to differ from [from], moved back to the start of that number. */
private fun changedFrom(from: String, to: String): Int {
    var i = 0
    while (i < from.length && i < to.length && from[i] == to[i]) i++
    if (i >= to.length) return 0 // nothing new at the end (e.g. "1.5.1" -> "1.5"): highlight it all
    // A change inside a number ("1.5.9" -> "1.5.10") highlights the whole number (digits only, so
    // a leading "v" stays plain); one that starts a new part ("1.5" -> "1.5.1") just the new part.
    val midToken = i < from.length && from[i].isDigit() && to[i].isDigit()
    if (midToken || (i > 0 && to[i - 1].isDigit() && to[i].isDigit())) {
        while (i > 0 && to[i - 1].isDigit()) i--
    }
    return i
}

/** "Major update" / "Minor update" / "Patch update" from the first number that changed. */
private fun changeCaption(from: String, to: String, newer: Boolean): String {
    if (from == to) return if (newer) "New build" else "Older build"
    if (!newer) return "Older version"
    val a = Regex("\\d+").findAll(from).map { it.value }.toList()
    val b = Regex("\\d+").findAll(to).map { it.value }.toList()
    // A missing number counts as a change there ("1.5" -> "1.5.1" is a patch).
    val first = (0 until maxOf(a.size, b.size)).firstOrNull { a.getOrNull(it) != b.getOrNull(it) } ?: return "Newer version"
    return when (first) {
        0 -> "Major update"
        1 -> "Minor update"
        else -> "Patch update"
    }
}

/** A status inside the card: a coloured dot beside one or two lines of text. */
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
    val style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            style = style,
            color = fg,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
            .height(50.dp)
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
