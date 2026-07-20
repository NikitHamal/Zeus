@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.agent

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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.model.AgentLlmCatalog
import com.zeus.code.model.AgentLlmProviderEntry
import com.zeus.code.model.AgentSession
import com.zeus.code.model.AgentUpload
import com.zeus.code.model.Workspace

@Composable
fun BackgroundAgentScreen(
    viewModel: BackgroundAgentViewModel,
    workspaces: List<Workspace>,
    onOpenWorkspace: (Workspace) -> Unit,
    onCloneBranch: (String, String, String?) -> Unit
) {
    val state by viewModel.state.collectAsState()
    when {
        state.booting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        !state.authorized -> AgentConnectScreen(state, viewModel)
        state.selectedSession != null -> AgentSessionScreen(
            state = state,
            viewModel = viewModel,
            workspaces = workspaces,
            onOpenWorkspace = onOpenWorkspace,
            onCloneBranch = onCloneBranch
        )
        else -> AgentDashboard(state, viewModel)
    }
}

@Composable
private fun AgentConnectScreen(state: AgentUiState, viewModel: BackgroundAgentViewModel) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item { Spacer(Modifier.height(22.dp)) }
        item {
            Surface(Modifier.size(78.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(38.dp)) }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NEBians Background Agent", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Securely run and review coding tasks from Zeus.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        state.pairing?.let { pairing ->
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Authorize this device", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Open NEBians and approve this one-time code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            modifier = Modifier.clickable { clipboard.setText(AnnotatedString(pairing.userCode)) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                pairing.userCode,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { uriHandler.openUri(pairing.verificationUrlComplete) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open NEBians")
                        }
                        TextButton(onClick = viewModel::cancelPairing) { Text("Cancel") }
                    }
                }
            }
        } ?: item {
            Button(onClick = viewModel::startPairing, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Link, null)
                Spacer(Modifier.width(8.dp))
                Text("Connect to NEBians")
            }
        }
        item {
            Text(
                "Authorization is encrypted in Android Keystore and remains available after app restarts until you revoke it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun defaultLlmLabel(catalog: AgentLlmCatalog?): String {
    val entry = catalog?.community?.firstOrNull { it.selectableForAgent && it.available }
        ?: return "Choose model"
    val model = entry.models.firstOrNull { it.id.equals(entry.defaultModel, true) }?.displayLabel
        ?: entry.defaultModel.ifBlank { entry.label }
    return "Default · $model"
}

@Composable
private fun AgentDashboard(state: AgentUiState, viewModel: BackgroundAgentViewModel) {
    var projectPicker by remember { mutableStateOf(false) }
    var addProject by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var modelPicker by remember { mutableStateOf(false) }
    var providersDialog by remember { mutableStateOf(false) }
    var focusedPreset by remember { mutableStateOf<AgentLlmProviderEntry?>(null) }
    var goal by remember { mutableStateOf("") }
    var uploads by remember { mutableStateOf<List<AgentUpload>>(emptyList()) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.prepareUploads(uris) { uploads = it }
    }
    var refreshing by remember { mutableStateOf(false) }
    var refreshCounter by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshCounter) {
        if (refreshCounter > 0 && !state.busy) {
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            refreshCounter++
            viewModel.refresh()
        }
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Background Agent", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (state.worker.healthy) "Worker online" else "Worker unavailable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box {
                        IconButton(onClick = { accountMenu = true }) { Icon(Icons.Rounded.MoreVert, "Agent account options") }
                        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("AI providers") },
                                onClick = { accountMenu = false; focusedPreset = null; providersDialog = true },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Disconnect NEBians") },
                                onClick = { accountMenu = false; confirmDisconnect = true },
                                leadingIcon = { Icon(Icons.Rounded.LinkOff, null) }
                            )
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("New task", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { projectPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Source, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.selectedProject?.repoFullName ?: "Choose project",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(onClick = { modelPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.llmSelection.label.ifBlank { defaultLlmLabel(state.llmCatalog) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(17.dp))
                        }
                        OutlinedTextField(
                            value = goal,
                            onValueChange = { goal = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("What should the agent change?") },
                            minLines = 3,
                            maxLines = 7
                        )
                        if (uploads.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                uploads.forEach { upload -> AssistChip(onClick = {}, label = { Text(upload.name, maxLines = 1) }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.AttachFile, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Attach")
                            }
                            Button(
                                onClick = {
                                    viewModel.createSession(goal, uploads) {
                                        goal = ""
                                        uploads = emptyList<AgentUpload>()
                                    }
                                },
                                enabled = state.selectedProject != null && goal.trim().length >= 10 && !state.busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Send, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start")
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.archivedMode) "Archived tasks" else "Recent tasks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(selected = !state.archivedMode, onClick = { viewModel.setArchivedMode(false) }, label = { Text("Recent") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = state.archivedMode, onClick = { viewModel.setArchivedMode(true) }, label = { Text("Archived") })
                }
            }
            if (state.sessions.isEmpty()) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(34.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(if (state.archivedMode) "No archived tasks" else "No tasks yet", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                items(state.sessions, key = { it.id }) { session ->
                    AgentSessionCard(
                        session = session,
                        unread = session.id in state.unreadIds,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
    }

    if (projectPicker) AgentProjectPickerDialog(
        state = state,
        onDismiss = { projectPicker = false },
        onSelect = { viewModel.selectProject(it); projectPicker = false },
        onAddProject = { projectPicker = false; addProject = true; viewModel.loadRepositories() }
    )
    if (addProject) AgentAddProjectDialog(state, viewModel) { addProject = false }
    if (modelPicker) AgentModelPickerDialog(
        state = state,
        onDismiss = { modelPicker = false },
        onSelectDefault = { viewModel.selectLlm(null); modelPicker = false },
        onSelect = { entry, modelId -> viewModel.selectLlm(entry, modelId); modelPicker = false },
        onManageProviders = { entry ->
            focusedPreset = entry
            modelPicker = false
            providersDialog = true
        }
    )
    if (providersDialog) AgentProvidersDialog(
        state = state,
        viewModel = viewModel,
        focusPreset = focusedPreset,
        onDismiss = { providersDialog = false; focusedPreset = null }
    )
    if (confirmDisconnect) AgentConfirmDialog(
        title = "Disconnect NEBians?",
        body = "This revokes the saved background-agent device token. Your tasks remain on NEBians.",
        destructive = true,
        confirmLabel = "Disconnect",
        onDismiss = { confirmDisconnect = false },
        onConfirm = { confirmDisconnect = false; viewModel.disconnect() }
    )
}

@Composable
private fun AgentSessionCard(session: AgentSession, unread: Boolean, viewModel: BackgroundAgentViewModel) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().clickable { viewModel.openSession(session) }) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AgentStatus(session.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    session.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                if (unread) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.padding(top = 5.dp).size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, "Task options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val active = session.status in listOf("queued", "preparing", "running")
                        DropdownMenuItem(
                            text = { Text(if (session.archived) "Restore" else "Archive") },
                            enabled = !active,
                            onClick = { menu = false; if (session.archived) viewModel.restore(session) else viewModel.archive(session) },
                            leadingIcon = { Icon(if (session.archived) Icons.Rounded.Restore else Icons.Rounded.Archive, null) }
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
            Text(
                session.repoFullName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(progress = { session.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(session.progressLabel, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatAgentTime(session.updatedAt), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (confirmDelete) AgentConfirmDialog(
        title = "Delete task?",
        body = "This permanently removes the task, worktree, attachments and generated artifacts.",
        destructive = true,
        onDismiss = { confirmDelete = false },
        onConfirm = { confirmDelete = false; viewModel.delete(session) }
    )
}
