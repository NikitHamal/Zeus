@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.local

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.local.LocalEvent
import com.zeus.code.local.LocalEventKind
import com.zeus.code.local.LocalTask
import com.zeus.code.local.LocalTaskStatus
import com.zeus.code.model.Workspace

@Composable
fun LocalTaskDetail(
    task: LocalTask?,
    state: LocalAgentUiState,
    viewModel: LocalAgentViewModel,
    onBack: () -> Unit,
    onOpenWorkspace: (Workspace) -> Unit
) {
    if (task == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This task no longer exists.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to local tasks")
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.workspaceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(8.dp))
                        LocalStatusChip(task.status)
                    }
                    Text(
                        "${task.choice.label.ifBlank { task.choice.model }} · ${task.progressLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (task.isActive) {
            item {
                LinearProgressIndicator(
                    progress = {
                        if (task.maxSteps > 0) (task.steps.toFloat() / task.maxSteps).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Goal", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(task.goal, style = MaterialTheme.typography.bodyMedium)
                    val branchLine = buildString {
                        append(formatLocalTime(task.createdAt))
                        if (task.workBranch.isNotBlank()) append("  ·  branch ${task.workBranch}")
                    }
                    Text(
                        branchLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ------------- outcome -------------
        if (task.summary.isNotBlank()) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(7.dp))
                            Text("Result", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(task.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (task.error.isNotBlank() && task.status != LocalTaskStatus.STOPPED) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Failed", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            task.error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else if (task.status == LocalTaskStatus.STOPPED && task.error.isNotBlank()) {
            item {
                Text(
                    task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ------------- changed files -------------
        if (task.changedFiles.isNotEmpty()) {
            item { SectionLabel("Changed files") }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    task.changedFiles.take(24).forEach { file ->
                        AssistChip(
                            onClick = {},
                            label = { Text(file, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // ------------- actions -------------
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (task.isActive) {
                    Button(onClick = viewModel::stopSelectedTask, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Stop, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop task")
                    }
                } else {
                    Button(onClick = { viewModel.retryTask(task.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Replay, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Run again")
                    }
                }
                FilledTonalButton(
                    onClick = {
                        state.workspaces.firstOrNull { it.path == task.workspacePath }
                            ?.let(onOpenWorkspace)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = state.workspaces.any { it.path == task.workspacePath }
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Workspace")
                }
            }
        }

        // ------------- timeline -------------
        item { SectionLabel("Activity") }
        if (task.events.isEmpty()) {
            item {
                Text(
                    "Waiting for the first step…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(task.events.size) { index ->
                EventRow(event = task.events[index])
            }
        }
    }
}

@Composable
private fun EventRow(event: LocalEvent) {
    val isTool = event.kind == LocalEventKind.TOOL || event.kind == LocalEventKind.FILE
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Surface(
            shape = CircleShape,
            color = when (event.kind) {
                LocalEventKind.ERROR -> MaterialTheme.colorScheme.errorContainer
                LocalEventKind.DONE -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    eventIcon(event.kind),
                    null,
                    Modifier.size(15.dp),
                    tint = when (event.kind) {
                        LocalEventKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.text,
                style = if (isTool) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = if (event.kind == LocalEventKind.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatLocalTime(event.at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp)
    )
}
