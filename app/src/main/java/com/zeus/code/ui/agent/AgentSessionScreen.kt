@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.agent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.model.AgentArtifact
import com.zeus.code.model.AgentMessage
import com.zeus.code.model.AgentSession
import com.zeus.code.model.AgentUpload
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AgentSessionScreen(
    state: AgentUiState,
    viewModel: BackgroundAgentViewModel,
    onCloneBranch: (String, String, String?) -> Unit
) {
    val session = state.selectedSession ?: return
    var tab by remember(session.id) { mutableIntStateOf(0) }
    var guidance by remember(session.id) { mutableStateOf("") }
    var uploads by remember(session.id) { mutableStateOf<List<AgentUpload>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingArtifact by remember { mutableStateOf<AgentArtifact?>(null) }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.prepareUploads(uris) { uploads = it }
    }
    val saveArtifact = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val artifact = pendingArtifact
        pendingArtifact = null
        if (uri != null && artifact != null) {
            context.contentResolver.openOutputStream(uri)?.let { output ->
                viewModel.downloadArtifact(artifact.downloadUrl, output) {}
            }
        }
    }
    val active = session.status in listOf("queued", "preparing", "running")

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AgentSessionHeader(
                session = session,
                active = active,
                menu = menu,
                onMenuChange = { menu = it },
                onBack = viewModel::closeSession,
                onArchive = { if (session.archived) viewModel.restore(session) else viewModel.archive(session) },
                onDelete = { confirmDelete = true }
            )
        }
        item { AgentProgressCard(session, viewModel, onCloneBranch) }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Activity") }, leadingIcon = { Icon(Icons.Rounded.History, null) })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Changes (${session.changedFiles.size})") }, leadingIcon = { Icon(Icons.Rounded.Code, null) })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Deliver") }, leadingIcon = { Icon(Icons.Rounded.FolderZip, null) })
            }
        }

        when (tab) {
            0 -> activityItems(session, guidance, uploads, state.busy, { types -> filePicker.launch(types) }, { value -> guidance = value }) {
                viewModel.sendMessage(guidance, uploads) {
                    guidance = ""
                    uploads = emptyList<AgentUpload>()
                }
            }
            1 -> changeItems(session)
            2 -> deliveryItems(
                session = session,
                active = active,
                onOpenRepository = { if (session.repoHtmlUrl.isNotBlank()) uriHandler.openUri(session.repoHtmlUrl) },
                onCloneBranch = { onCloneBranch(session.cloneUrl, session.repoFullName.substringAfterLast('/'), session.workBranch.ifBlank { session.sourceBranch }) },
                onDownload = { artifact -> pendingArtifact = artifact; saveArtifact.launch(artifact.fileName) },
                onPush = { viewModel.runAction("push") },
                onOpenPullRequest = { viewModel.runAction("open_pr") }
            )
            else -> Unit
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

@Composable
private fun AgentSessionHeader(
    session: AgentSession,
    active: Boolean,
    menu: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(session.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.repoFullName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { onMenuChange(true) }) { Icon(Icons.Rounded.MoreVert, "Task options") }
            DropdownMenu(expanded = menu, onDismissRequest = { onMenuChange(false) }) {
                DropdownMenuItem(
                    text = { Text(if (session.archived) "Restore" else "Archive") },
                    enabled = !active,
                    onClick = { onMenuChange(false); onArchive() },
                    leadingIcon = { Icon(if (session.archived) Icons.Rounded.Restore else Icons.Rounded.Archive, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete permanently") },
                    enabled = !active,
                    onClick = { onMenuChange(false); onDelete() },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                )
            }
        }
    }
}

@Composable
private fun AgentProgressCard(
    session: AgentSession,
    viewModel: BackgroundAgentViewModel,
    onCloneBranch: (String, String, String?) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AgentStatus(session.status)
                Spacer(Modifier.weight(1f))
                Text("${session.progress}%", fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { session.progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            Text(session.progressLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${session.sourceBranch} → ${session.workBranch.ifBlank { "preparing" }}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.status in listOf("queued", "preparing", "running")) {
                    FilledTonalButton(onClick = { viewModel.control("pause") }) { Icon(Icons.Rounded.Pause, null); Spacer(Modifier.width(5.dp)); Text("Pause") }
                }
                if (session.status in listOf("paused", "waiting", "failed", "completed")) {
                    Button(onClick = { viewModel.control("resume") }) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("Resume") }
                }
                if (session.status !in listOf("completed", "failed", "cancelled")) {
                    OutlinedButton(onClick = { viewModel.control("stop") }) { Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(5.dp)); Text("Stop") }
                }
                if (session.workBranch.isNotBlank()) {
                    OutlinedButton(onClick = { onCloneBranch(session.cloneUrl, session.repoFullName.substringAfterLast('/'), session.workBranch) }) {
                        Icon(Icons.Rounded.CloudDownload, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Clone branch")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.activityItems(
    session: AgentSession,
    guidance: String,
    uploads: List<AgentUpload>,
    busy: Boolean,
    launchPicker: (Array<String>) -> Unit,
    onGuidanceChange: (String) -> Unit,
    onSend: () -> Unit
) {
    if (session.summary.isNotBlank() || session.testSummary.isNotBlank() || session.lastError.isNotBlank()) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (session.summary.isNotBlank()) { Text("Summary", style = MaterialTheme.typography.titleMedium); Text(session.summary) }
                    if (session.testSummary.isNotBlank()) { HorizontalDivider(); Text("Validation", style = MaterialTheme.typography.titleMedium); Text(session.testSummary) }
                    if (session.lastError.isNotBlank()) { HorizontalDivider(); Text("Last error", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error); Text(session.lastError) }
                }
            }
        }
    }
    item { Text("Conversation", style = MaterialTheme.typography.titleLarge) }
    if (session.messages.isEmpty()) {
        item { AgentEmptyPanel("No messages yet", "The worker activity will appear here as the task runs.") }
    } else {
        items(session.messages, key = { it.id }) { message -> AgentMessageCard(message) }
    }
    item {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = guidance,
                    onValueChange = onGuidanceChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Add guidance") },
                    minLines = 3,
                    maxLines = 8
                )
                if (uploads.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uploads.forEach { upload -> AssistChip(onClick = {}, label = { Text(upload.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Rounded.AttachFile, null) }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { launchPicker(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.AttachFile, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Attach")
                    }
                    Button(
                        onClick = onSend,
                        enabled = (guidance.isNotBlank() || uploads.isNotEmpty()) && !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Send, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Send")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.changeItems(session: AgentSession) {
    item { Text("Changed files", style = MaterialTheme.typography.titleLarge) }
    if (session.changedFiles.isEmpty()) {
        item { AgentEmptyPanel("No changes yet", "Files will appear after the agent edits the isolated worktree.") }
    } else {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column {
                    session.changedFiles.forEachIndexed { index, path ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(path, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
                        }
                        if (index < session.changedFiles.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
    item { Text("Patch", style = MaterialTheme.typography.titleLarge) }
    item {
        if (session.diff.isBlank()) {
            AgentEmptyPanel("No patch available", "The generated diff will appear here when changes are ready.")
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                SelectionContainer {
                    Column(Modifier.padding(14.dp).horizontalScroll(rememberScrollState())) {
                        Text(session.diff, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, softWrap = false)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.deliveryItems(
    session: AgentSession,
    active: Boolean,
    onOpenRepository: () -> Unit,
    onCloneBranch: () -> Unit,
    onDownload: (AgentArtifact) -> Unit,
    onPush: () -> Unit,
    onOpenPullRequest: () -> Unit
) {
    item {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Continue locally", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Clone the agent branch into Zeus, then use the Workspaces tab to edit, pull, commit and push.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onCloneBranch, enabled = session.cloneUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CloudDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clone ${session.workBranch.ifBlank { session.sourceBranch }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onOpenRepository, enabled = session.repoHtmlUrl.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.OpenInBrowser, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open repository")
                }
            }
        }
    }
    item { Text("Artifacts", style = MaterialTheme.typography.titleLarge) }
    if (session.artifacts.isEmpty()) {
        item { AgentEmptyPanel("No artifacts yet", "Changed-files ZIP and patch downloads are generated when the task completes.") }
    } else {
        items(session.artifacts.values.toList(), key = { it.kind }) { artifact ->
            OutlinedButton(onClick = { onDownload(artifact) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FolderZip, null)
                Spacer(Modifier.width(8.dp))
                Text(artifact.fileName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(artifact.sizeBytes), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPush, enabled = !active && session.workBranch.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Push") }
            FilledTonalButton(onClick = onOpenPullRequest, enabled = !active && session.workBranch.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Open PR") }
        }
    }
    item { Text("Delivery activity", style = MaterialTheme.typography.titleLarge) }
    if (session.actions.isEmpty()) {
        item { AgentEmptyPanel("No delivery actions", "Push and pull-request actions will be recorded here.") }
    } else {
        items(session.actions, key = { it.id }) { action ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(action.action.replace('_', ' ').replaceFirstChar { it.uppercase() }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(action.status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatAgentTime(action.createdAt), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun AgentMessageCard(message: AgentMessage) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                when (message.role) { "user" -> "You"; "tool" -> "Tool"; else -> "Agent" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            if (message.content.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                SelectionContainer { Text(message.content, fontFamily = if (message.role == "tool") FontFamily.Monospace else FontFamily.Default) }
            }
            if (message.attachments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("${message.attachments.size} attachment(s)", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AgentEmptyPanel(title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.AutoAwesome, null)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun AgentStatus(status: String) {
    val color = when (status) {
        "completed" -> MaterialTheme.colorScheme.tertiaryContainer
        "failed", "cancelled" -> MaterialTheme.colorScheme.errorContainer
        "running", "preparing" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (status == "completed") Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal fun formatAgentTime(timestamp: Long): String = if (timestamp <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024f * 1024f))
    value >= 1024L -> "%.0f KB".format(value / 1024f)
    else -> "$value B"
}
