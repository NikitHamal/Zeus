@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.local.LocalEventKind
import com.zeus.code.local.LocalTask
import com.zeus.code.local.LocalTaskStatus
import com.zeus.code.model.AgentMessage
import com.zeus.code.model.Workspace
import com.zeus.code.ui.agent.AgentStatusChip
import com.zeus.code.ui.agent.AssistantBubble
import com.zeus.code.ui.agent.CompactAction
import com.zeus.code.ui.agent.EmptyPane
import com.zeus.code.ui.agent.SectionLabel
import com.zeus.code.ui.agent.TabPill
import com.zeus.code.ui.agent.ToolCallRow
import com.zeus.code.ui.agent.UserBubble
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ======================================================================= */
/* Local task detail — same layout as the NEBians session screen           */
/* ======================================================================= */

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
    var tab by remember(task.id) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = LocalTaskStatus.isActive(task.status)

    Column(Modifier.fillMaxSize()) {
        // -----------------------------------------------------------------
        // Compact header
        // -----------------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to tasks")
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    task.goal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${task.workspaceName} · ${task.choice.label.ifBlank { task.choice.model }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AgentStatusChip(task.status)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.MoreVert, "Task options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Run again") },
                        enabled = !active,
                        onClick = { menu = false; viewModel.retryTask(task.id) },
                        leadingIcon = { Icon(Icons.Rounded.RestartAlt, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete permanently") },
                        enabled = !active,
                        onClick = { menu = false; confirmDelete = true },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                    )
                }
            }
        }

        // -----------------------------------------------------------------
        // Task status + controls
        // -----------------------------------------------------------------
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    task.progressLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CompactAction(Icons.Rounded.Stop, "Stop", visible = active) {
                    viewModel.stopSelectedTask()
                }
                CompactAction(
                    Icons.Rounded.RestartAlt, "Run again",
                    visible = !active
                ) { viewModel.retryTask(task.id) }
            }
            StepMeter(task)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TabPill("Activity", tab == 0) { tab = 0 }
                TabPill("Changes (${task.changedFiles.size})", tab == 1) { tab = 1 }
                TabPill("Deliver", tab == 2) { tab = 2 }
            }
            Spacer(Modifier.height(8.dp))
        }

        // -----------------------------------------------------------------
        // Tab content
        // -----------------------------------------------------------------
        when (tab) {
            0 -> ActivityFeedTab(task, active)
            1 -> ChangesListTab(task)
            2 -> DeliverLocalTab(task, state, onOpenWorkspace)
        }
    }

    if (confirmDelete) {
        com.zeus.code.ui.agent.AgentConfirmDialog(
            title = "Delete task?",
            body = "This removes the local task history. Workspace files are untouched.",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.deleteTask(task.id); onBack() }
        )
    }
}

/** Live step budget meter, styled after the session context meter. */
@Composable
private fun StepMeter(task: LocalTask) {
    val percent = if (task.maxSteps > 0) {
        ((task.steps * 100f) / task.maxSteps).toInt().coerceIn(0, 100)
    } else 0
    val color = when {
        task.status == LocalTaskStatus.FAILED -> MaterialTheme.colorScheme.error
        percent >= 85 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Steps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${task.steps}/${task.maxSteps}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/* ======================================================================= */
/* Activity tab — conversation rendered exactly like cloud sessions         */
/* ======================================================================= */

@Composable
private fun ActivityFeedTab(task: LocalTask, active: Boolean) {
    val listState = rememberLazyListState()
    val messages = remember(task.id, task.events.size, task.summary) { localTaskMessages(task) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages.size) { index ->
            val message = messages[index]
            when {
                message.role == "user" -> UserBubble(message)
                message.role == "tool" -> ToolCallRow(message)
                else -> AssistantBubble(message)
            }
        }
        if (active) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        task.progressLabel.ifBlank { "Working..." },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Maps the durable local-event log onto the same chat model the cloud agent
 * renders, so both surfaces share one visual language:
 *
 *  - the goal opens the transcript as a user bubble
 *  - `TOOL` events pair with their following result (`FILE` ok / `ERROR` fail)
 *    and render as collapsible tool rows with diff detection
 *  - model commentary, completion notes and the final summary are assistant
 *    bubbles with markdown
 */
internal fun localTaskMessages(task: LocalTask): List<AgentMessage> {
    val out = mutableListOf<AgentMessage>()
    if (task.goal.isNotBlank()) {
        out += AgentMessage(id = "${task.id}-goal", role = "user", content = task.goal, createdAt = task.createdAt)
    }
    var pendingToolLabel: String? = null
    var pendingToolAt = 0L

    fun flushDanglingTool() {
        val label = pendingToolLabel ?: return
        out += AgentMessage(
            id = "${task.id}-tool-${out.size}",
            role = "tool",
            label = label,
            content = "",
            createdAt = pendingToolAt
        )
        pendingToolLabel = null
    }

    task.events.forEach { event ->
        when (event.kind) {
            LocalEventKind.TOOL -> {
                flushDanglingTool()
                pendingToolLabel = event.text
                pendingToolAt = event.at
            }
            LocalEventKind.FILE -> {
                val label = pendingToolLabel
                if (label != null) {
                    out += AgentMessage(
                        id = "${task.id}-tool-${out.size}",
                        role = "tool",
                        label = label,
                        content = event.text,
                        createdAt = event.at
                    )
                    pendingToolLabel = null
                } else {
                    out += AgentMessage(
                        id = "${task.id}-out-${event.id}",
                        role = "tool",
                        label = "output",
                        content = event.text,
                        createdAt = event.at
                    )
                }
            }
            LocalEventKind.ERROR -> {
                val label = pendingToolLabel
                if (label != null) {
                    out += AgentMessage(
                        id = "${task.id}-tool-${out.size}",
                        role = "tool",
                        label = label,
                        content = event.text,
                        metadata = buildJsonObject { put("ok", false) },
                        createdAt = event.at
                    )
                    pendingToolLabel = null
                } else {
                    out += AgentMessage(
                        id = "${task.id}-err-${event.id}",
                        role = "assistant",
                        content = "**Error** — ${event.text}",
                        createdAt = event.at
                    )
                }
            }
            LocalEventKind.LLM, LocalEventKind.DONE -> out += AgentMessage(
                id = "${task.id}-msg-${event.id}",
                role = "assistant",
                content = event.text,
                createdAt = event.at
            )
            else -> out += AgentMessage(
                id = "${task.id}-info-${event.id}",
                role = "assistant",
                content = "_${event.text}_",
                createdAt = event.at
            )
        }
    }
    flushDanglingTool()

    if (task.summary.isNotBlank()) {
        out += AgentMessage(
            id = "${task.id}-summary",
            role = "assistant",
            content = task.summary,
            createdAt = task.completedAt
        )
    }
    return out
}

/* ======================================================================= */
/* Changes tab                                                              */
/* ======================================================================= */

@Composable
private fun ChangesListTab(task: LocalTask) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionLabel("Changed files (${task.changedFiles.size})") }
        if (task.changedFiles.isEmpty()) {
            item { EmptyPane(Icons.Rounded.Description, "No changes yet", "Files appear after the agent edits the workspace.") }
        } else {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        task.changedFiles.forEachIndexed { index, path ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Description, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    path,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index < task.changedFiles.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/* ======================================================================= */
/* Deliver tab                                                              */
/* ======================================================================= */

@Composable
private fun DeliverLocalTab(
    task: LocalTask,
    state: LocalAgentUiState,
    onOpenWorkspace: (Workspace) -> Unit
) {
    val workspace = state.workspaces.firstOrNull { it.path == task.workspacePath }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionLabel("Continue locally") }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (workspace != null) Icons.Rounded.CheckCircle else Icons.Rounded.FolderOpen,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (workspace != null) "Workspace on this device" else "Workspace unavailable",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                listOfNotNull(
                                    task.workspaceName,
                                    workspace?.currentBranch ?: task.workBranch.takeIf { it.isNotBlank() },
                                    if (workspace?.gitRepository == true) "git" else null
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { workspace?.let(onOpenWorkspace) },
                        enabled = workspace != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Open workspace")
                    }
                }
            }
        }
        item { SectionLabel("Version control") }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (workspace?.gitRepository == true) "Git repository detected" else "Plain folder (no git)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (workspace?.gitRepository == true) {
                            "The agent commits milestones locally on ${task.workBranch.ifBlank { "the current branch" }}. Review and push from Workspaces."
                        } else {
                            "Changes live directly in the workspace folder. Create a git repository to get version control."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Source, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Model · ${task.choice.label.ifBlank { task.choice.model }}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatLocalTime(task.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
