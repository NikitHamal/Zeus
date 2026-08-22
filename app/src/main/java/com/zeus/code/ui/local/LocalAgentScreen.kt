@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zeus.code.local.LocalEventKind
import com.zeus.code.local.LocalTask
import com.zeus.code.local.LocalTaskStatus
import com.zeus.code.model.Workspace
import java.text.DateFormat
import java.util.Date

/* ------------------------------------------------------------------------- */
/* Root                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun LocalAgentScreen(
    viewModel: LocalAgentViewModel,
    workspaces: List<Workspace>,
    onOpenWorkspace: (Workspace) -> Unit
) {
    val state by viewModel.state.collectAsState()
    when {
        state.booting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.selectedTaskId != null -> LocalTaskDetail(
            task = state.tasks.firstOrNull { it.id == state.selectedTaskId },
            state = state,
            viewModel = viewModel,
            onBack = viewModel::closeTask,
            onOpenWorkspace = onOpenWorkspace
        )
        else -> LocalDashboard(state, viewModel)
    }
}

/* ------------------------------------------------------------------------- */
/* Dashboard                                                                 */
/* ------------------------------------------------------------------------- */

@Composable
private fun LocalDashboard(state: LocalAgentUiState, viewModel: LocalAgentViewModel) {
    var workspacePicker by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    var providersDialog by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var goal by remember { mutableStateOf("") }

    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Result ignored: background execution works either way, the
        // notification is simply suppressed when denied.
    }

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val activeTasks = state.tasks.filter { LocalTaskStatus.isActive(it.status) }
    val finishedTasks = state.tasks.filterNot { LocalTaskStatus.isActive(it.status) }

    PullToRefreshBox(
        isRefreshing = state.busy,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Local Mode", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Coding agent running on this device · works offline of NEBians",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { accountMenu = true }) { Icon(Icons.Rounded.MoreVert, "Local agent options") }
                        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("AI providers") },
                                onClick = { accountMenu = false; providersDialog = true },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear finished tasks") },
                                onClick = { accountMenu = false; confirmClear = true },
                                leadingIcon = { Icon(Icons.Rounded.ClearAll, null) }
                            )
                        }
                    }
                }
            }

            // ---------------- composer ----------------
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier.weight(1f).clickable { workspacePicker = true }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    null,
                                    Modifier.size(17.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.selectedWorkspace?.name ?: "Choose workspace",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (state.selectedWorkspace == null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(2.dp))
                        HorizontalRule()

                        TextField(
                            value = goal,
                            onValueChange = { goal = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("What should the agent change?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 2,
                            maxLines = 6
                        )

                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = {
                                    ensureNotificationPermission()
                                    modelPicker = true
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Bolt,
                                        contentDescription = "Choose model",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(
                                Modifier.weight(1f).clickable { modelPicker = true }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    state.selection.label.ifBlank { "Choose model" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            val canSend = state.selectedWorkspace != null &&
                                state.selection.isValid &&
                                goal.trim().length >= 10 &&
                                !state.busy
                            Surface(
                                onClick = {
                                    ensureNotificationPermission()
                                    viewModel.startTask(goal) {
                                        goal = ""
                                    }
                                },
                                enabled = canSend,
                                shape = CircleShape,
                                color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = "Run locally",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!state.hasAnyProvider) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth().clickable { providersDialog = true }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No AI provider configured", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Add your OpenCode Zen key (free) or any OpenAI-compatible endpoint to run tasks locally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ---------------- active tasks ----------------
            item {
                Text(
                    if (activeTasks.isEmpty()) "Recent tasks" else "Running · ${activeTasks.size}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (activeTasks.isEmpty() && finishedTasks.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Smartphone, null, Modifier.size(34.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No local tasks yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Pick a workspace, describe the change and run — everything stays on this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
            items(activeTasks, key = { it.id }) { task ->
                LocalTaskCard(task = task, viewModel = viewModel)
            }
            if (finishedTasks.isNotEmpty()) {
                item {
                    Text("Finished", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                }
                items(finishedTasks, key = { it.id }) { task ->
                    LocalTaskCard(task = task, viewModel = viewModel)
                }
            }
        }
    }

    if (workspacePicker) {
        LocalWorkspacePickerDialog(
            state = state,
            onSelect = { viewModel.selectWorkspace(it); workspacePicker = false },
            onDismiss = { workspacePicker = false }
        )
    }
    if (modelPicker) {
        LocalModelPickerDialog(
            state = state,
            onDismiss = { modelPicker = false },
            onSelect = { choice -> viewModel.select(choice); },
            onManageProviders = {
                modelPicker = false
                providersDialog = true
            }
        )
    }
    if (providersDialog) {
        LocalProvidersDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { providersDialog = false }
        )
    }
    if (confirmClear) {
        com.zeus.code.ui.agent.AgentConfirmDialog(
            title = "Clear finished tasks?",
            body = "Removes all completed, failed and stopped tasks from history. Running tasks stay.",
            destructive = false,
            confirmLabel = "Clear",
            onDismiss = { confirmClear = false },
            onConfirm = { confirmClear = false; viewModel.deleteFinished() }
        )
    }
}

@Composable
private fun HorizontalRule() {
    Box(
        Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

/* ------------------------------------------------------------------------- */
/* Task card + workspace picker                                              */
/* ------------------------------------------------------------------------- */

@Composable
private fun LocalTaskCard(task: LocalTask, viewModel: LocalAgentViewModel) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().clickable { viewModel.openTask(task.id) }) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                LocalStatusChip(task.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    task.goal,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, "Task options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete permanently") },
                            enabled = !task.isActive,
                            onClick = { menu = false; confirmDelete = true },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Re-run") },
                            enabled = !task.isActive,
                            onClick = { menu = false; viewModel.retryTask(task.id) },
                            leadingIcon = { Icon(Icons.Rounded.Replay, null) }
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Smartphone,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    "${task.workspaceName} · ${task.choice.label.ifBlank { task.choice.model }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.progressLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatLocalTime(task.updatedAt), style = MaterialTheme.typography.labelMedium)
            }
            if (task.status == LocalTaskStatus.RUNNING || task.status == LocalTaskStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = {
                        if (task.maxSteps > 0) (task.steps.toFloat() / task.maxSteps).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    if (confirmDelete) {
        com.zeus.code.ui.agent.AgentConfirmDialog(
            title = "Delete task?",
            body = "This removes the local task history. Workspace files are untouched.",
            destructive = true,
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; viewModel.deleteTask(task.id) }
        )
    }
}

@Composable
internal fun LocalStatusChip(status: String) {
    val (container, content) = when (status) {
        LocalTaskStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LocalTaskStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        LocalTaskStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LocalTaskStatus.STOPPED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LocalWorkspacePickerDialog(
    state: LocalAgentUiState,
    onSelect: (Workspace) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose workspace") },
        text = {
            LazyColumn(Modifier.height(380.dp)) {
                items(state.filteredWorkspaces, key = { it.path }) { workspace ->
                    ListItem(
                        headlineContent = {
                            Text(workspace.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    workspace.currentBranch,
                                    if (workspace.gitRepository) "git" else null
                                ).joinToString(" · ").ifBlank { "folder" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.FolderOpen, null)
                        },
                        trailingContent = {
                            if (state.selectedWorkspace?.path == workspace.path) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable { onSelect(workspace) }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

/* ------------------------------------------------------------------------- */
/* Helpers                                                                   */
/* ------------------------------------------------------------------------- */

internal fun formatLocalTime(timestamp: Long): String =
    if (timestamp <= 0L) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

/** Re-export used by the detail screen for its event timeline icons. */
internal fun eventIcon(kind: String) = when (kind) {
    LocalEventKind.TOOL -> Icons.Rounded.Tune
    LocalEventKind.FILE -> Icons.Rounded.Description
    LocalEventKind.ERROR -> Icons.Rounded.ErrorOutline
    LocalEventKind.DONE -> Icons.Rounded.CheckCircle
    LocalEventKind.LLM -> Icons.Rounded.AutoAwesome
    else -> Icons.Rounded.Info
}
