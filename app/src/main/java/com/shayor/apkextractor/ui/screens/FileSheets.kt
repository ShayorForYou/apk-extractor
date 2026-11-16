package com.shayor.apkextractor.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.ui.MainViewModel
import com.shayor.apkextractor.ui.MoveState
import com.shayor.apkextractor.ui.components.CellDivider
import com.shayor.apkextractor.ui.components.IconTile
import com.shayor.apkextractor.ui.components.InsetGroup
import com.shayor.apkextractor.ui.components.IosSheet
import com.shayor.apkextractor.ui.components.PrimaryButton
import com.shayor.apkextractor.ui.components.SecondaryButton
import com.shayor.apkextractor.ui.components.rememberFileSize
import com.shayor.apkextractor.ui.components.rememberIosSheetState
import com.shayor.apkextractor.ui.theme.iosColors
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------------------------------
// Clear History
// -------------------------------------------------------------------------------------------------

/**
 * Asks how to clear History: delete the saved files too (the usual intent: they are what History
 * lists), or only empty the list and keep the files. Either can be undone for a few seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearHistorySheet(
    count: Int,
    bytes: Long,
    location: String,
    onDelete: () -> Unit,
    onKeepFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = iosColors
    val sheetState = rememberIosSheetState()
    val scope = rememberCoroutineScope()
    // Slide the sheet away first, then act, so the toast doesn't appear under a closing sheet.
    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            then()
            onDismiss()
        }
    }
    val size = rememberFileSize(bytes)
    IosSheet(onDismiss, sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(Icons.Rounded.Delete, p.red, size = 56.dp)
            Spacer(Modifier.height(16.dp))
            Text("Clear History?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(if (count == 1) "1 saved file" else "$count saved files")
                    if (bytes > 0) append(" · $size")
                    append(" in $location")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = p.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                if (count == 1) "Delete File" else "Delete $count Files",
                onClick = { close(onDelete) },
                color = p.red,
                icon = Icons.Rounded.Delete,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "Keep Files, Clear List",
                onClick = { close(onKeepFiles) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "You can undo this for a few seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = p.tertiaryLabel,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Moving saved files to a new folder
// -------------------------------------------------------------------------------------------------

/** Shows the move sheet whenever the ViewModel offers (or runs) a move of saved files. */
@Composable
fun MoveFilesHost(vm: MainViewModel) {
    val state by vm.move.collectAsStateWithLifecycle()
    // Android 9 and older: moving into Download needs the storage permission.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startMove() else vm.dismissMove()
    }
    val current = state
    if (current !is MoveState.Idle) {
        MoveFilesSheet(
            state = current,
            onMove = {
                if (vm.needsLegacyStoragePermission()) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else vm.startMove()
            },
            onCancel = vm::cancelMove,
            onClose = vm::dismissMove,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveFilesSheet(
    state: MoveState,
    onMove: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val moving = state is MoveState.Moving
    val movingNow by rememberUpdatedState(moving)
    // While files are moving the sheet stays put: only Cancel stops it.
    val sheetState = rememberIosSheetState(confirmValueChange = { it != SheetValue.Hidden || !movingNow })
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() }
    }
    if (moving) KeepScreenOn()

    IosSheet(onDismiss = { if (!movingNow) onClose() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // One slot per kind of state: progress ticks update the slot instead of re-animating it.
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                        .using(SizeTransform(clip = false))
                },
                label = "move",
            ) { s ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        is MoveState.Offer -> OfferContent(s, onMove = onMove, onKeep = close)
                        is MoveState.Moving -> MovingContent(s, onCancel = onCancel)
                        is MoveState.Done -> DoneContent(s, onDone = close)
                        MoveState.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferContent(offer: MoveState.Offer, onMove: () -> Unit, onKeep: () -> Unit) {
    val p = iosColors
    val count = offer.entries.size
    val size = rememberFileSize(offer.bytes)
    FolderTransfer(from = offer.from, to = offer.to, moving = false)
    Spacer(Modifier.height(20.dp))
    Text("Move Your Saved Files?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
        buildString {
            append(if (count == 1) "1 file" else "$count files")
            if (offer.bytes > 0) append(" ($size)")
            append(if (count == 1) " is" else " are")
            append(" still in ${offer.from}. Move ")
            append(if (count == 1) "it" else "them")
            append(" to ${offer.to} to keep all your backups in one place.")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = p.secondaryLabel,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(
        if (count == 1) "Move File" else "Move $count Files",
        onClick = onMove,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    SecondaryButton("Keep Them There", onClick = onKeep, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    Text(
        "History keeps track of your files either way.",
        style = MaterialTheme.typography.bodySmall,
        color = p.tertiaryLabel,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun MovingContent(state: MoveState.Moving, onCancel: () -> Unit) {
    val p = iosColors
    val progress = animateFloatAsState(state.progress, spring(stiffness = Spring.StiffnessLow), label = "progress")
    val finished = state.index >= state.total
    FolderTransfer(from = state.from, to = state.to, moving = !finished)
    Spacer(Modifier.height(20.dp))
    Text(
        if (finished) "Finishing Up" else "Moving Files",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (finished) "All files are in place" else "${state.index + 1} of ${state.total}  ·  ${state.currentFile}",
        style = MaterialTheme.typography.bodyMedium,
        color = p.secondaryLabel,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(22.dp))
    ProgressBar(progress)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val done = rememberFileSize(state.bytesDone)
        val total = rememberFileSize(state.bytesTotal)
        Text(
            if (state.bytesTotal > 0) "$done of $total" else "",
            style = MaterialTheme.typography.labelMedium,
            color = p.secondaryLabel,
        )
        Text(
            "${(state.progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = p.secondaryLabel,
        )
    }
    Spacer(Modifier.height(22.dp))
    // Same height either way, so the sheet doesn't jump as the move ends.
    Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) {
        if (!finished) SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DoneContent(state: MoveState.Done, onDone: () -> Unit) {
    val p = iosColors
    val ok = state.moved > 0 || (state.failed.isEmpty() && !state.cancelled)
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = shown,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f), initialScale = 0.4f) + fadeIn(),
    ) {
        Box(
            Modifier
                .padding(top = 8.dp)
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
    Spacer(Modifier.height(16.dp))
    Text(
        when {
            state.cancelled -> "Move Stopped"
            state.moved == 0 && state.failed.isNotEmpty() -> "Couldn't Move Files"
            state.moved == 1 -> "1 File Moved"
            else -> "${state.moved} Files Moved"
        },
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        when {
            state.cancelled && state.moved == 0 -> "Nothing was moved. Your files are where they were."
            state.cancelled -> "${state.moved} moved to ${state.to}. The rest are where they were."
            state.failed.isNotEmpty() && state.moved > 0 -> "Now in ${state.to}. Some files stayed where they were:"
            state.failed.isNotEmpty() -> "Your files are where they were."
            else -> "Now in ${state.to}"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = p.secondaryLabel,
        textAlign = TextAlign.Center,
    )
    if (state.failed.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        InsetGroup {
            Column(Modifier.heightIn(max = 200.dp).sheetScroll(rememberScrollState())) {
                state.failed.forEachIndexed { i, (name, reason) ->
                    if (i > 0) CellDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = p.red)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
}

/**
 * Two folders joined by a dotted track: where the files are, and where they go. While moving, a
 * file glides along the track. The animation runs in the draw phase only (no recomposition).
 */
@Composable
private fun FolderTransfer(from: String, to: String, moving: Boolean) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        FolderLabel(from, Icons.Rounded.FolderOpen, p.gray, Modifier.weight(1f))
        Box(
            Modifier
                .padding(top = 14.dp)
                .width(72.dp)
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val track = p.tertiaryLabel
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(
                    track,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
                )
            }
            if (!moving) {
                // Which way the files go.
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iosColors.sheet)
                        .padding(3.dp),
                )
            } else {
                val glide = rememberInfiniteTransition(label = "glide")
                val t = glide.animateFloat(
                    0f, 1f,
                    infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                    label = "t",
                )
                Icon(
                    Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            val x = t.value
                            translationX = (x - 0.5f) * (72.dp.toPx() - 22.dp.toPx())
                            // Fade in at the start, out at the end.
                            alpha = (minOf(x, 1f - x) * 6f).coerceIn(0f, 1f)
                        },
                )
            }
        }
        FolderLabel(to, Icons.Rounded.Folder, accent, Modifier.weight(1f))
    }
}

@Composable
private fun FolderLabel(name: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, color, size = 56.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = iosColors.secondaryLabel,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A rounded track filled up to [progress]; read in the draw phase so ticks don't recompose. */
@Composable
private fun ProgressBar(progress: State<Float>) {
    val p = iosColors
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(p.tertiaryFill, cornerRadius = r)
                val w = size.width * progress.value.coerceIn(0f, 1f)
                if (w > 0f) drawRoundRect(accent, size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
            },
    )
}

/** Long moves shouldn't be cut short by the screen timing out. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
