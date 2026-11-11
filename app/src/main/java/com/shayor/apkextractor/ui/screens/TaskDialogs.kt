package com.shayor.apkextractor.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shayor.apkextractor.ui.TaskKind
import com.shayor.apkextractor.ui.TaskState
import com.shayor.apkextractor.ui.components.AppIcon
import com.shayor.apkextractor.ui.components.SecondaryButton

@Composable
fun TaskProgressDialog(task: TaskState.Running, onCancel: () -> Unit) {
    // Keep the screen awake so long batch backups aren't interrupted by the screen timing out.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val progress by animateFloatAsState(task.progress, label = "progress")
    val colors = MaterialTheme.colorScheme
    val merging = task.stage != null

    Dialog(
        onDismissRequest = {}, // only the Cancel button stops the task
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
                    val trackColor = colors.surfaceContainerHigh
                    if (merging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(112.dp),
                            strokeWidth = 5.dp,
                            trackColor = trackColor,
                            color = colors.tertiary,
                            strokeCap = StrokeCap.Round,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(112.dp),
                            strokeWidth = 5.dp,
                            trackColor = trackColor,
                            color = colors.tertiary,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                        )
                    }
                    AppIcon(task.currentPackage, task.currentIconKey, 64.dp)
                    // Done: a check pops onto the ring before the share sheet / result appears.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = task.finished,
                        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceContainerLow)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                val many = task.total > 1
                Text(
                    if (task.finished && many) "${task.savedCount} apps" else task.currentLabel,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val share = task.kind == TaskKind.SHARE
                val status = when {
                    task.finished && share -> "Ready to share"
                    task.finished -> "Saved"
                    else -> buildString {
                        append(
                            when {
                                merging -> "Merging"
                                share -> "Preparing"
                                else -> "Extracting"
                            },
                        )
                        if (!merging) append(" · ${(progress * 100).toInt()}%")
                        if (many) append(" · ${task.index + 1} of ${task.total}")
                    }
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (task.finished) colors.primary else colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
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
