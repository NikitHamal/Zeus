@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.agent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.model.AgentArtifact
import com.zeus.code.model.AgentMessage
import com.zeus.code.model.AgentSession
import com.zeus.code.model.AgentTodo
import com.zeus.code.model.AgentUpload
import com.zeus.code.model.Workspace
import com.zeus.code.ui.DiffView
import java.text.DateFormat
import java.util.Date
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/* ======================================================================= */
/* Session — chat-first layout with a pinned composer                       */
/* ======================================================================= */

@Composable
internal fun AgentSessionScreen(
    state: AgentUiState,
    viewModel: BackgroundAgentViewModel,
    workspaces: List<Workspace>,
    onOpenWorkspace: (Workspace) -> Unit,
    onCloneBranch: (String, String, String?) -> Unit
) {
    val session = state.selectedSession ?: return
    var tab by remember(session.id) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = session.status in listOf("queued", "preparing", "running")

    Column(Modifier.fillMaxSize().imePadding()) {
        // -----------------------------------------------------------------
        // Compact header
        // -----------------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::closeSession, modifier = Modifier.size(34.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to tasks")
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (session.llm.label.isBlank()) session.repoFullName
                    else "${session.repoFullName} · ${session.llm.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AgentStatusChip(session.status)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.MoreVert, "Task options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Compact context") },
                        onClick = { menu = false; viewModel.sendMessage("/compact", emptyList()) {} },
                        leadingIcon = { Icon(Icons.Rounded.Compress, null) }
                    )
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

        // -----------------------------------------------------------------
        // Live progress strip
        // -----------------------------------------------------------------
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { session.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.weight(1f).height(4.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${session.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    session.progressLabel.ifBlank { session.status },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                CompactAction(Icons.Rounded.Pause, "Pause", visible = active) { viewModel.control("pause") }
                CompactAction(
                    Icons.Rounded.PlayArrow, "Resume",
                    visible = session.status in listOf("paused", "waiting", "failed", "completed")
                ) { viewModel.control("resume") }
                CompactAction(
                    Icons.Rounded.Stop, "Stop",
                    visible = session.status !in listOf("completed", "failed", "cancelled")
                ) { viewModel.control("stop") }
            }
            ContextMeter(session)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TabPill("Activity", tab == 0) { tab = 0 }
                TabPill("Changes (${session.changedFiles.size})", tab == 1) { tab = 1 }
                TabPill("Deliver", tab == 2) { tab = 2 }
            }
            Spacer(Modifier.height(8.dp))
        }

        // -----------------------------------------------------------------
        // Tab content
        // -----------------------------------------------------------------
        when (tab) {
            0 -> ActivityTab(session, state, viewModel)
            1 -> ChangesTab(session)
            2 -> DeliverTab(session, active, workspaces, onOpenWorkspace, onCloneBranch, viewModel)
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

/** Live context-window usage, mirroring the NEBians session page meter. */
@Composable
private fun ContextMeter(session: AgentSession) {
    val ctx = session.context
    if (ctx.windowTokens <= 0) return
    val percent = ctx.percent.coerceIn(0, 100)
    val color = when {
        percent >= 80 -> MaterialTheme.colorScheme.error
        percent >= 65 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Context",
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
            "$percent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            " · ${formatTokenCount(ctx.estimatedTokens)}/${formatTokenCount(ctx.windowTokens)}" +
                if (ctx.compactions > 0) " · ${ctx.compactions} compacted" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatTokenCount(value: Int): String = when {
    value >= 1_000_000 -> "${(value / 100_000) / 10f}M"
    value >= 1_000 -> "${value / 1000}k"
    else -> value.toString()
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        modifier = Modifier.height(30.dp)
    )
}

@Composable
private fun CompactAction(icon: ImageVector, label: String, visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, label, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ======================================================================= */
/* Activity tab — conversation with pinned composer                         */
/* ======================================================================= */

@Composable
private fun ActivityTab(session: AgentSession, state: AgentUiState, viewModel: BackgroundAgentViewModel) {
    var guidance by remember(session.id) { mutableStateOf("") }
    var uploads by remember(session.id) { mutableStateOf<List<AgentUpload>>(emptyList()) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.prepareUploads(uris) { uploads = it }
    }

    // Keep the newest message in view — no more scrolling through old activity.
    LaunchedEffect(session.messages.size) {
        if (session.messages.isNotEmpty()) {
            listState.animateScrollToItem(session.messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (session.summary.isNotBlank() || session.lastError.isNotBlank()) {
                item { SessionOutcomeCard(session) }
            }
            if (session.messages.isEmpty()) {
                item {
                    EmptyPane(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Task queued",
                        body = "Worker output appears here as the task runs."
                    )
                }
            }
            if (session.todos.isNotEmpty()) {
                item { AgentPlanCard(session.todos) }
            }
            items(session.messages, key = { it.id }) { message ->
                when {
                    message.isThought -> ThoughtRow(message)
                    message.isCommand -> CommandRow(message)
                    message.role == "user" -> UserBubble(message)
                    message.role == "tool" -> ToolCallRow(message)
                    else -> AssistantBubble(message)
                }
            }
            if (session.status in listOf("queued", "preparing", "running")) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            session.progressLabel.ifBlank { "Working..." },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // -----------------------------------------------------------------
        // Pinned composer
        // -----------------------------------------------------------------
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                if (uploads.isNotEmpty()) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uploads.forEachIndexed { index, upload ->
                            AssistChip(
                                onClick = { uploads = uploads.toMutableList().also { it.removeAt(index) } },
                                label = { Text(upload.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.AttachFile, null, Modifier.size(15.dp)) },
                                trailingIcon = { Icon(Icons.Rounded.Stop, "Remove", Modifier.size(13.dp)) }
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.size(38.dp)
                    ) { Icon(Icons.Rounded.Add, "Attach files", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedTextField(
                        value = guidance,
                        onValueChange = { guidance = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Guide the agent...", style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium,
                        minLines = 1,
                        maxLines = 5,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            viewModel.sendMessage(guidance, uploads) {
                                guidance = ""
                                uploads = emptyList<AgentUpload>()
                            }
                        },
                        enabled = (guidance.isNotBlank() || uploads.isNotEmpty()) && !state.busy,
                        modifier = Modifier.size(38.dp)
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", Modifier.size(17.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SessionOutcomeCard(session: AgentSession) {
    val isError = session.lastError.isNotBlank()
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            if (session.summary.isNotBlank()) {
                SessionOutcomeCardSection("Outcome") { MarkdownContent(session.summary, textSize = MaterialTheme.typography.bodyMedium.fontSize) }
            }
            if (session.testSummary.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                SessionOutcomeCardSection("Validation") { Text(session.testSummary, style = MaterialTheme.typography.bodySmall) }
            }
            if (isError) {
                Spacer(Modifier.height(4.dp))
                SessionOutcomeCardSection("Error", MaterialTheme.colorScheme.onErrorContainer) {
                    Text(session.lastError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun SessionOutcomeCardSection(
    label: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onTertiaryContainer,
    content: @Composable () -> Unit
) {
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = color)
    content()
}

/* ======================================================================= */
/* Message rendering                                                        */
/* ======================================================================= */

@Composable
private fun UserBubble(message: AgentMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.content.isNotBlank()) {
                    MarkdownContent(message.content, textSize = MaterialTheme.typography.bodyMedium.fontSize)
                }
                if (message.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${message.attachments.size} attachment(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        MessageTime(message.createdAt)
    }
}

@Composable
private fun AssistantBubble(message: AgentMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp),
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.content.isNotBlank()) {
                    MarkdownContent(message.content, textSize = MaterialTheme.typography.bodyMedium.fontSize)
                } else {
                    Text(message.label.ifBlank { "..." }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        MessageTime(message.createdAt)
    }
}

@Composable
private fun MessageTime(timestamp: Long) {
    if (timestamp <= 0) return
    Text(
        formatAgentTime(timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/* ======================================================================= */
/* Message metadata helpers — thought chips, slash commands, tool status    */
/* ======================================================================= */

private fun AgentMessage.metaString(key: String): String =
    metadata?.get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private val AgentMessage.isThought: Boolean
    get() = role == "assistant" && metaString("kind") == "thought"

private val AgentMessage.isCommand: Boolean
    get() = role == "user" && metaString("kind") == "command"

private val AgentMessage.toolSucceeded: Boolean
    get() = metadata?.get("ok")?.jsonPrimitive?.booleanOrNull ?: true

private val AgentMessage.thoughtDurationMs: Long
    get() = metadata?.get("durationMs")?.jsonPrimitive?.longOrNull ?: 0L

/* ======================================================================= */
/* Model reasoning — collapsible "Thought for N seconds" (LMArena style)    */
/* ======================================================================= */

@Composable
private fun ThoughtRow(message: AgentMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val seconds = message.thoughtDurationMs.let { if (it > 0) (it / 1000L).coerceAtLeast(1L) else 0L }
    val label = if (seconds > 0) "Thought for $seconds second${if (seconds == 1L) "" else "s"}" else "Thought for a moment"
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Psychology, null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                if (expanded) "Hide reasoning" else "Show reasoning",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
                    .alpha(0.78f)
            ) {
                MarkdownContent(message.content, textSize = MaterialTheme.typography.bodySmall.fontSize)
            }
        }
    }
}

/* ======================================================================= */
/* Slash-command echo rows (/compact)                                       */
/* ======================================================================= */

@Composable
private fun CommandRow(message: AgentMessage) {
    val note = if (message.metaString("command") == "compact")
        " — context compaction will run at the start of the next iteration" else ""
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Terminal, null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            message.content + note,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ======================================================================= */
/* Live plan / todo checklist                                               */
/* ======================================================================= */

@Composable
private fun AgentPlanCard(todos: List<AgentTodo>) {
    var expanded by remember { mutableStateOf(true) }
    val done = todos.count { it.status == "completed" }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Checklist, null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Plan",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text(
                        "$done/${todos.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                    if (expanded) "Collapse plan" else "Expand plan",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                    todos.forEach { todo ->
                        val completed = todo.status == "completed"
                        val inProgress = todo.status == "in_progress"
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when {
                                    completed -> Icons.Rounded.CheckCircle
                                    inProgress -> Icons.Rounded.PlayCircle
                                    else -> Icons.Rounded.RadioButtonUnchecked
                                },
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = when {
                                    completed -> MaterialTheme.colorScheme.tertiary
                                    inProgress -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                todo.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = if (completed) Modifier.alpha(0.8f) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ======================================================================= */
/* Tool calls — friendly verbs instead of raw JSON/log text                 */
/* ======================================================================= */

private data class ToolVerb(val label: String, val icon: ImageVector)

private fun toolVerb(message: AgentMessage): ToolVerb {
    val raw = (message.label.ifBlank { message.content.take(40) }).lowercase()
    return when {
        "read" in raw || "open_file" in raw || "view" in raw || "cat" in raw ->
            ToolVerb("Read", Icons.Rounded.Description)
        "write" in raw || "create_file" in raw || "save_file" in raw ->
            ToolVerb("Wrote", Icons.Rounded.Edit)
        "edit" in raw || "patch" in raw || "replace" in raw ->
            ToolVerb("Edited", Icons.Rounded.Edit)
        "mkdir" in raw || "create_dir" in raw || "create_directory" in raw ->
            ToolVerb("Created dir", Icons.Rounded.CreateNewFolder)
        "list" in raw || raw.startsWith("ls") || "tree" in raw ->
            ToolVerb("Listed dir", Icons.Rounded.FolderOpen)
        "delete" in raw || "remove" in raw || raw.startsWith("rm") ->
            ToolVerb("Deleted", Icons.Rounded.Delete)
        "copy" in raw || raw.startsWith("cp") ->
            ToolVerb("Copied", Icons.Rounded.ContentCopy)
        "move" in raw || "rename" in raw || raw.startsWith("mv") ->
            ToolVerb("Moved", Icons.Rounded.Upload)
        "search" in raw || "grep" in raw || "find" in raw ->
            ToolVerb("Searched", Icons.Rounded.Search)
        "git" in raw || "commit" in raw || "checkout" in raw ->
            ToolVerb("Git", Icons.Rounded.Source)
        "run" in raw || "exec" in raw || "bash" in raw || "shell" in raw || "command" in raw || "test" in raw || "build" in raw ->
            ToolVerb("Ran", Icons.Rounded.Terminal)
        else -> ToolVerb(message.label.ifBlank { "Step" }.replace('_', ' '), Icons.Rounded.AutoAwesome)
    }
}

private fun toolTarget(message: AgentMessage): String {
    // Prefer the path/command straight after the tool name in the label.
    val label = message.label.trim()
    if (label.isNotBlank()) {
        val parts = label.split(" ", limit = 2)
        if (parts.size == 2 && parts[1].isNotBlank()) return parts[1].take(80)
    }
    val firstLine = message.content.lineSequence().firstOrNull()?.trim().orEmpty()
    return firstLine.take(80)
}

@Composable
private fun ToolCallRow(message: AgentMessage) {
    var expanded by remember(message.id) { mutableStateOf(false) }
    val verb = toolVerb(message)
    val target = toolTarget(message)
    val ok = message.toolSucceeded
    val tint = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = message.content.isNotBlank()) { expanded = !expanded }
                .padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                verb.icon, null,
                modifier = Modifier.size(13.dp),
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(7.dp))
            Text(
                verb.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (ok) MaterialTheme.colorScheme.onSurface else tint
            )
            if (target.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    target,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!ok) {
                Text(
                    "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(4.dp))
            }
            if (message.content.isNotBlank()) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded && message.content.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
            ) {
                SelectionContainer {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

/* ======================================================================= */
/* Changes tab                                                              */
/* ======================================================================= */

@Composable
private fun ChangesTab(session: AgentSession) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionLabel("Changed files (${session.changedFiles.size})") }
        if (session.changedFiles.isEmpty()) {
            item { EmptyPane(Icons.Rounded.FolderOpen, "No changes yet", "Files appear after the agent edits the worktree.") }
        } else {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column {
                        session.changedFiles.forEachIndexed { index, path ->
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
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (index < session.changedFiles.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
        item { SectionLabel("Patch") }
        item {
            if (session.diff.isBlank()) {
                EmptyPane(Icons.Rounded.Code, "No patch yet", "The diff appears once changes are ready.")
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        DiffView(session.diff)
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

private fun workspaceMatchesSession(session: AgentSession, workspaces: List<Workspace>): Workspace? {
    val repo = session.repoFullName.trim().lowercase()
    if (repo.isBlank()) return null
    val branch = session.workBranch.ifBlank { session.sourceBranch }.lowercase()
    fun urlOf(ws: Workspace) = ws.remoteUrl?.lowercase().orEmpty()
    fun branchOf(ws: Workspace) = ws.currentBranch?.lowercase().orEmpty()
    // Strongest: remote URL contains owner/repo and the branch matches.
    workspaces.firstOrNull { urlOf(it).contains(repo + ".git") && (branch.isBlank() || branchOf(it) == branch) }
        ?.let { return it }
    // Fallback: URL contains owner/repo regardless of branch.
    return workspaces.firstOrNull { urlOf(it).contains(repo) }
}

@Composable
private fun DeliverTab(
    session: AgentSession,
    active: Boolean,
    workspaces: List<Workspace>,
    onOpenWorkspace: (Workspace) -> Unit,
    onCloneBranch: (String, String, String?) -> Unit,
    viewModel: BackgroundAgentViewModel
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var pendingArtifact by remember { mutableStateOf<AgentArtifact?>(null) }
    val saveArtifact = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val artifact = pendingArtifact
        pendingArtifact = null
        if (uri != null && artifact != null) {
            context.contentResolver.openOutputStream(uri)?.let { output ->
                viewModel.downloadArtifact(artifact.downloadUrl, output) {}
            }
        }
    }
    val localMatch = workspaceMatchesSession(session, workspaces)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionLabel("Continue locally") }
        item {
            if (localMatch != null) {
                // Repo already cloned on-device — straight into the workspace.
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Already cloned on this device", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${localMatch.name}${localMatch.currentBranch?.let { " · $it" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { onOpenWorkspace(localMatch) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Open workspace")
                        }
                    }
                }
            } else {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Not cloned yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Clone the agent branch into Zeus to edit, pull, commit and push from Workspaces.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                onCloneBranch(
                                    session.cloneUrl,
                                    session.repoFullName.substringAfterLast('/'),
                                    session.workBranch.ifBlank { session.sourceBranch }
                                )
                            },
                            enabled = session.cloneUrl.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Clone ${session.workBranch.ifBlank { session.sourceBranch }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { if (session.repoHtmlUrl.isNotBlank()) uriHandler.openUri(session.repoHtmlUrl) },
                enabled = session.repoHtmlUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Open repository on GitHub")
            }
        }

        item { SectionLabel("Artifacts") }
        if (session.artifacts.isEmpty()) {
            item { EmptyPane(Icons.Rounded.FolderZip, "No artifacts yet", "Changed-files ZIPs and patches are generated at completion.") }
        } else {
            items(session.artifacts.values.toList(), key = { it.kind }) { artifact ->
                OutlinedCard(
                    Modifier.fillMaxWidth().clickable { pendingArtifact = artifact; saveArtifact.launch(artifact.fileName) }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(artifact.fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatBytes(artifact.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionLabel("Delivery") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { viewModel.runAction("push") },
                    enabled = !active && session.workBranch.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Push branch", maxLines = 1) }
                FilledTonalButton(
                    onClick = { viewModel.runAction("open_pr") },
                    enabled = !active && session.workBranch.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CallMerge, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Open PR", maxLines = 1)
                }
            }
        }
        if (session.actions.isNotEmpty()) {
            items(session.actions, key = { it.id }) { action ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.History, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            action.action.replace('_', ' ').replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(action.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatAgentTime(action.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/* ======================================================================= */
/* Small shared pieces                                                      */
/* ======================================================================= */

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun EmptyPane(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun AgentStatusChip(status: String) {
    val (color, icon) = when (status) {
        "completed" -> MaterialTheme.colorScheme.tertiaryContainer to Icons.Rounded.CheckCircle
        "failed", "cancelled" -> MaterialTheme.colorScheme.errorContainer to Icons.Rounded.ErrorOutline
        "running", "preparing" -> MaterialTheme.colorScheme.primaryContainer to Icons.Rounded.RestartAlt
        else -> MaterialTheme.colorScheme.surfaceVariant to Icons.Rounded.AutoAwesome
    }
    Surface(color = color, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/* Kept for the dashboard session cards. */
@Composable
internal fun AgentStatus(status: String) = AgentStatusChip(status)

internal fun formatAgentTime(timestamp: Long): String = if (timestamp <= 0) "" else
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024f * 1024f))
    value >= 1024L -> "%.0f KB".format(value / 1024f)
    else -> "$value B"
}
