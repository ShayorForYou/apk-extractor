package com.shayor.apkextractor.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    task.currentLabel,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                val verb = when {
                    merging -> "Merging"
                    task.kind == TaskKind.SHARE -> "Preparing"
                    else -> "Extracting"
                }
                val status = buildString {
                    append(verb)
                    if (!merging) append(" · ${(progress * 100).toInt()}%")
                    if (task.total > 1) append(" · ${task.index + 1} of ${task.total}")
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth(), height = 48.dp)
            }
        }
    }
}
