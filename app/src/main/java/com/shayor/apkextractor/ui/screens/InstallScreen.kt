package com.shayor.apkextractor.ui.screens

import com.shayor.apkextractor.ui.components.GroupRadius
import com.shayor.apkextractor.ui.components.SectionLabelStyle
import com.shayor.apkextractor.ui.components.CloseButton
import androidx.compose.ui.draw.drawWithCache
import com.shayor.apkextractor.ui.components.groupEdge
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.CheckCircle
import com.shayor.apkextractor.ui.components.ActivityIndicator
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
            if (backups.isEmpty()) {
                item(key = "empty") {
                    SectionFooter("Apps you back up show up here, ready to reinstall in a tap.")
                }
            } else {
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
 * The way in: a roomier card than the rows below it, lit by a faint accent glow from its corner
 * (a static gradient, drawn once). The file glyph and a "Browse" pill share the top line; the
 * title, what it does and the formats it opens read below. The whole card is the button and sinks
 * a touch while pressed.
 */
@Composable
private fun PickFileCard(onPick: () -> Unit) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    val glow = accent.copy(alpha = if (p.dark) 0.22f else 0.12f)
    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(source, pressed = 0.98f)
            .clip(shape)
            .background(cellColor())
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(glow, Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.75f,
                    ),
                    radius = size.width * 0.75f,
                    center = Offset(size.width, 0f),
                )
            }
            .border(1.dp, p.separator, shape)
            .clickable(source, indication = null, role = Role.Button, onClickLabel = "Choose a file", onClick = onPick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = if (p.dark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Browse",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("Install from a file", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick an app file from anywhere on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.secondaryLabel,
            maxLines = 2,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (format in PickFormats) {
                Text(
                    format,
                    style = SectionLabelStyle.copy(letterSpacing = 0.8.sp),
                    color = p.secondaryLabel,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(p.tertiaryFill)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

private val PickFormats = listOf("APK", "APKS", "XAPK", "APKM")

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
            .groupEdge(first, last, iosColors.separator)
            .drawBehind {
                if (!first) {
                    val x = 72.dp.toPx()
                    drawRect(separator, topLeft = Offset(x, 0f), size = Size(size.width - x, 0.5.dp.toPx()))
                }
            }
            .clickable(onClick = onInstall)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.packageName, "${entry.packageName}@history", 42.dp, backupUri = entry.uri)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // One quiet line of facts; the format tag only for bundles, the less common case.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(
                        versionLabel(entry.versionName).take(14),
                        fileSize.takeIf { entry.sizeBytes > 0 },
                        relativeTime(entry.time),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = p.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
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
// Install sheet — one compact card: the app in a header row, two quiet lines of facts, and the
// actions. Every slot keeps its height in every state, so the sheet never resizes (a resize during
// its slide-in made it stop and jump); progress is a ring drawn round the icon.
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            AppHeader(state, ui.title, ui.subtitle, onClose = if (busy) null else vm::dismiss)
            Spacer(Modifier.height(18.dp))
            InfoLines(ui.detail, ui.caption)
            Spacer(Modifier.height(20.dp))
            Actions(ui)
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
                )
            } else {
                SheetUi(
                    "Couldn't read this file", "Make sure it's an APK or APK bundle",
                    warn(state.message),
                    "Choose another file", { vm.dismiss(); onPickFile() },
                )
            }
        }
    }
}

/** Icon, name and package in one row, with a round close button while it can be closed. */
@Composable
private fun AppHeader(state: InstallState, title: String?, subtitle: String?, onClose: (() -> Unit)?) {
    val p = iosColors
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, color = p.label)
    val subtitleStyle = MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SheetIcon(state)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            // Reserves one title line and one package line, so the placeholder and the text match.
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
                            Placeholder(width = 140.dp, height = 16.dp)
                            Spacer(Modifier.height(8.dp))
                            Placeholder(width = 100.dp, height = 10.dp)
                        } else {
                            FitText(t, titleStyle, minSize = 14.sp)
                            // An app without a name is titled by its package: don't repeat it.
                            if (sub != null && sub != t) {
                                Spacer(Modifier.height(2.dp))
                                Text(sub, style = subtitleStyle, color = p.tertiaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(30.dp)) {
            androidx.compose.animation.AnimatedVisibility(onClose != null, enter = fadeIn(), exit = fadeOut()) {
                CloseButton(onClick = { onClose?.invoke() })
            }
        }
    }
}

/**
 * App icon with a thin accent ring round it while installing (a gliding arc while the length is
 * unknown), and a check badge once installed. The ring is drawn, never laid out.
 */
@Composable
private fun SheetIcon(state: InstallState) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    val pkg = when (state) {
        is InstallState.Ready -> state.pkg
        is InstallState.Installing -> state.pkg
        is InstallState.Done -> state.pkg
        is InstallState.Failed -> state.pkg
        else -> null
    }
    val installing = state as? InstallState.Installing
    val active = installing != null && !installing.waitingForUser
    val ringAlpha by androidx.compose.animation.core.animateFloatAsState(if (active) 1f else 0f, label = "ring")
    val sweep by androidx.compose.animation.core.animateFloatAsState(installing?.progress ?: 0f, label = "sweep")
    // Spins only while the step's length is unknown.
    val spin = if (active && installing?.progress == null) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "spin").animateFloat(
            0f,
            360f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "spinAngle",
        )
    } else {
        null
    }
    val track = p.tertiaryFill
    val shape = RoundedCornerShape(IconSize * 0.26f)
    Box(
        Modifier
            .size(IconBox)
            .drawBehind {
                if (ringAlpha <= 0f) return@drawBehind
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, alpha = ringAlpha, style = Stroke(stroke))
                if (spin != null) {
                    drawArc(accent, spin.value - 90f, 90f, false, topLeft, arcSize, alpha = ringAlpha, style = Stroke(stroke, cap = StrokeCap.Round))
                } else {
                    drawArc(accent, -90f, 360f * sweep, false, topLeft, arcSize, alpha = ringAlpha, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(IconSize)) {
            val icon = pkg?.icon
            when {
                icon != null -> Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
                state is InstallState.Failed -> Box(
                    Modifier.fillMaxSize().clip(shape).background(p.tertiaryFill),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = p.secondaryLabel, modifier = Modifier.size(24.dp)) }
                pkg != null -> Box(
                    Modifier.fillMaxSize().clip(shape).background(p.tertiaryFill),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Android, contentDescription = null, tint = p.secondaryLabel, modifier = Modifier.size(24.dp)) }
                else -> Placeholder(IconSize, IconSize, shape)
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = state is InstallState.Done,
            enter = androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 500f)) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(p.sheet)
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(p.green),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) }
        }
    }
}

private val IconSize = 48.dp
private val IconBox = 58.dp

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
 * Two lines under the header: the facts (tags, or installed → new) or a status, then a quiet hint.
 * A status with no hint may take both lines.
 */
@Composable
private fun InfoLines(detail: Detail, caption: String?) {
    ReserveHeight(
        reserve = {
            Column {
                Box(Modifier.height(TagHeight))
                Spacer(Modifier.height(6.dp))
                Text("", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        },
    ) {
        AnimatedContent(
            detail to (caption ?: defaultHint(detail)),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentAlignment = Alignment.CenterStart,
            label = "info",
            modifier = Modifier.fillMaxSize(),
        ) { (d, cap) ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                when (d) {
                    is Detail.Status -> StatusLine(d.text, d.tone, maxLines = if (cap == null) 2 else 1)
                    is Detail.Facts -> FactTags(d.pkg)
                    is Detail.Compare -> VersionCompare(d.pkg)
                }
                if (cap != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(cap, style = MaterialTheme.typography.bodySmall, color = iosColors.tertiaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val TagHeight = 26.dp

/** A quiet line for states that have no hint of their own, so the two lines always read as a pair. */
private fun defaultHint(detail: Detail): String? = when (detail) {
    is Detail.Facts -> "New on this phone"
    is Detail.Compare -> when {
        detail.pkg.versionCode == detail.pkg.installedVersionCode -> "Replaces the installed copy, keeps its data"
        detail.pkg.versionCode > (detail.pkg.installedVersionCode ?: 0) -> "Your data stays as it is"
        else -> null
    }
    is Detail.Status -> when {
        detail.text.startsWith("Waiting") -> "Approve it in the system prompt"
        else -> null
    }
}

/** A small capsule of fact: grey, or tinted with [tint]. */
@Composable
private fun Tag(text: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val p = iosColors
    Box(
        modifier
            .height(TagHeight)
            .clip(CircleShape)
            .background(tint?.copy(alpha = if (p.dark) 0.16f else 0.10f) ?: p.tertiaryFill)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = tint ?: p.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A new app: version, size and the Android it needs, as tags. */
@Composable
private fun FactTags(pkg: PackageToInstall) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        // The version gets whatever room is left: some are long ("26.43.30 (260400-1006944126)").
        Tag(versionLabel(pkg.versionName), Modifier.weight(1f, fill = false))
        Tag(rememberFileSize(pkg.totalBytes))
        Tag("Android ${androidName(pkg.minSdk)}+")
    }
}

/**
 * Already installed: "installed → new" where only the part of the new version that differs is
 * tinted (accent for an update, orange for an older one), and a tag naming the change. The same
 * version shows once.
 */
@Composable
private fun VersionCompare(pkg: PackageToInstall) {
    val p = iosColors
    val installedCode = pkg.installedVersionCode ?: return
    val fromName = versionLabel(pkg.installedVersionName.orEmpty().ifBlank { installedCode.toString() })
    val toName = versionLabel(pkg.versionName)
    val size = rememberFileSize(pkg.totalBytes)
    if (pkg.versionCode == installedCode) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Tag(toName, Modifier.weight(1f, fill = false))
            Tag("Installed", tint = p.green)
            Tag(size)
        }
        return
    }
    val newer = pkg.versionCode > installedCode
    val tint = if (newer) MaterialTheme.colorScheme.primary else p.orange
    val split = changedFrom(fromName, toName)
    val versions = buildAnnotatedString {
        withStyle(SpanStyle(color = p.tertiaryLabel)) { append(fromName) }
        withStyle(SpanStyle(color = p.tertiaryLabel)) { append("  →  ") }
        append(toName.take(split))
        withStyle(SpanStyle(color = tint)) { append(toName.drop(split)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FitText(versions, MaterialTheme.typography.titleSmall.copy(color = p.label), Modifier.weight(1f, fill = false), minSize = 11.sp)
        Spacer(Modifier.width(10.dp))
        Tag(changeCaption(fromName, toName, newer), tint = tint)
        Spacer(Modifier.width(6.dp))
        Tag(size)
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

/** A status line: a small symbol (or a spinner while waiting) and the text in its colour. */
@Composable
private fun StatusLine(text: String, tone: Tone, maxLines: Int) {
    val colors = MaterialTheme.colorScheme
    val p = iosColors
    val fg = when (tone) {
        Tone.Neutral -> p.secondaryLabel
        Tone.Accent -> colors.primary
        Tone.Success -> p.green
        Tone.Warning -> p.orange
    }
    Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        when (tone) {
            Tone.Neutral -> ActivityIndicator(size = 15.dp)
            Tone.Success -> Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Tone.Warning -> Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
            Tone.Accent -> Unit
        }
        if (tone != Tone.Accent) Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = fg,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The main action, with a quieter second one (Cancel / Done) beside it when there is one. */
@Composable
private fun Actions(ui: SheetUi) {
    Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
        val footer = ui.footer
        if (footer != null) {
            QuietButton(footer.first, footer.second, Modifier.weight(0.6f))
            Spacer(Modifier.width(10.dp))
        }
        MainButton(ui.button, ui.onClick, ui.busy, Modifier.weight(1f))
    }
}

/**
 * The accent capsule. While busy it turns into a soft accent tint with the accent label; the ring
 * round the icon shows the progress.
 */
@Composable
private fun MainButton(label: String, onClick: (() -> Unit)?, busy: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val p = iosColors
    val source = remember { MutableInteractionSource() }
    val container by androidx.compose.animation.animateColorAsState(
        if (busy) colors.primary.copy(alpha = if (p.dark) 0.16f else 0.10f) else colors.primary, label = "btnBg",
    )
    val content by androidx.compose.animation.animateColorAsState(
        if (busy) colors.primary else colors.onPrimary, label = "btnFg",
    )
    Box(
        modifier
            .fillMaxHeight()
            .pressScale(source)
            .clip(CircleShape)
            .drawBehind { drawRect(container) }
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
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = content, maxLines = 1)
        }
    }
}

@Composable
private fun QuietButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val p = iosColors
    val source = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxHeight()
            .pressScale(source)
            .clip(CircleShape)
            .background(p.tertiaryFill)
            .border(1.dp, p.separator, CircleShape)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = p.label, maxLines = 1)
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
