package com.shayor.apkextractor.ui.screens

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shayor.apkextractor.data.ExportStage
import com.shayor.apkextractor.ui.TaskKind
import com.shayor.apkextractor.ui.TaskState
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.SecondaryButton
import com.shayor.apkextractor.ui.theme.iosColors
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects the task here, not at the app root: progress ticks ~30 times a second, and reading it
 * higher up would recompose the whole app (lists, pager, bars) on every tick.
 */
@Composable
fun TaskProgressHost(task: StateFlow<TaskState>, onCancel: () -> Unit) {
    val state by task.collectAsStateWithLifecycle()
    (state as? TaskState.Running)?.let { TaskProgressDialog(it, onCancel) }
}

@Composable
fun TaskProgressDialog(task: TaskState.Running, onCancel: () -> Unit) {
    // Keep the screen awake so long batch backups aren't interrupted by the screen timing out.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // Held as State and read only inside the ring's draw lambda, so the smooth animation between
    // progress ticks redraws the ring without recomposing the dialog.
    val progress = animateFloatAsState(task.progress, spring(stiffness = Spring.StiffnessLow), label = "progress")
    val colors = MaterialTheme.colorScheme
    val many = task.total > 1
    val share = task.kind == TaskKind.SHARE
    val stage = task.stage
    // A single app being merged has no byte progress to show; a batch keeps its overall ring.
    val mergingSingle = stage != null && !many && !task.finished

    Dialog(
        onDismissRequest = {}, // only the Cancel button stops the task
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(34.dp),
            color = iosColors.elevatedCell,
            shadowElevation = 24.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProgressRing(task, progress, indeterminate = mergingSingle)
                Spacer(Modifier.height(20.dp))

                // Crossfades from app to app in a batch instead of snapping.
                AnimatedContent(
                    if (task.finished && many) "${task.savedCount} apps" else task.currentLabel,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "title",
                ) { title ->
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val status = when {
                    task.finished && share -> "Ready to share"
                    task.finished -> "Saved"
                    else -> buildString {
                        append(stage?.label ?: if (share) "Preparing" else "Extracting")
                        if (many) append(" · ${task.index + 1} of ${task.total}")
                    }
                }
                val statusColor by animateColorAsState(
                    if (task.finished) iosColors.green else iosColors.secondaryLabel,
                    label = "statusColor",
                )
                Text(status, style = MaterialTheme.typography.bodyMedium, color = statusColor, maxLines = 1)

                Spacer(Modifier.height(18.dp))
                if (mergingSingle && stage != null) StepTrack(stage) else Stats(task, progress)

                Spacer(Modifier.height(20.dp))
                if (task.finished) {
                    // Same height as the button, so nothing jumps.
                    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (share) "Opening share options…" else "Almost done…",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant,
                        )
                    }
                } else {
                    SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth(), height = 46.dp)
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(task: TaskState.Running, progress: State<Float>, indeterminate: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        if (indeterminate) {
            CircularProgressIndicator(
                modifier = Modifier.size(112.dp),
                strokeWidth = 4.dp,
                trackColor = iosColors.tertiaryFill,
                color = colors.primary,
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(112.dp),
                strokeWidth = 4.dp,
                trackColor = iosColors.tertiaryFill,
                color = colors.primary,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        }
        AnimatedContent(
            task.currentPackage to task.currentIconKey,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.8f)) togetherWith
                    (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.1f))
            },
            label = "icon",
        ) { (pkg, iconKey) -> AppIcon(pkg, iconKey, 64.dp) }

        // Done: a check pops onto the ring before the share sheet / result appears.
        AnimatedVisibility(
            visible = task.finished,
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iosColors.elevatedCell)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(iosColors.green),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Percent · size copied · time left (batches only: a single app is usually done in a second or two). */
@Composable
private fun Stats(task: TaskState.Running, progress: State<Float>) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(iosColors.tertiaryFill)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (task.finished) Stat("100%", "done") else PercentStat(progress)
        Stat(
            Formatter.formatShortFileSize(context, task.bytesDone),
            "of ${Formatter.formatShortFileSize(context, task.bytesTotal)}",
        )
        if (task.total > 1) Stat(if (task.finished) "0:00" else timeLeft(task) ?: "–", "left")
    }
}

/** Follows the animated ring, so the number and the arc always agree; only this text recomposes. */
@Composable
private fun PercentStat(progress: State<Float>) {
    Stat("${(progress.value * 100).toInt()}%", "done")
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Shown while a single app is merged: one segment per step, filling as each step starts. */
@Composable
private fun StepTrack(stage: ExportStage) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..stage.steps) {
                val color by animateColorAsState(
                    when {
                        i < stage.step -> colors.primary
                        i == stage.step -> colors.primary.copy(alpha = 0.5f)
                        else -> iosColors.tertiaryFill
                    },
                    label = "step",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Merging · step ${stage.step} of ${stage.steps}",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * Remaining time from the average speed so far. That average includes merges and per-file overhead,
 * so it settles quickly and doesn't jump around. Hidden for the first moments, with too little to go on.
 */
private fun timeLeft(task: TaskState.Running): String? {
    if (task.startedAt <= 0L || task.bytesDone <= 0L) return null
    val elapsed = SystemClock.elapsedRealtime() - task.startedAt
    if (elapsed < 1_500L) return null
    val remainingBytes = (task.bytesTotal - task.bytesDone).coerceAtLeast(0)
    val seconds = (elapsed * remainingBytes / task.bytesDone + 999) / 1000
    return if (seconds >= 3600) "${seconds / 3600}h ${seconds % 3600 / 60}m" else "%d:%02d".format(seconds / 60, seconds % 60)
}
