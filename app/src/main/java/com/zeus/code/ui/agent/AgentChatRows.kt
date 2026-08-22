@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.model.AgentMessage
import com.zeus.code.ui.DiffView
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/* ======================================================================= */
/* Shared agent chat rendering — used by cloud sessions AND Local Mode      */
/* ======================================================================= */

/** Live context-window usage, mirroring the NEBians session page meter. */
@Composable
internal fun ContextMeter(estimatedTokens: Int, windowTokens: Int, percent: Int, compactions: Int) {
    if (windowTokens <= 0) return
    val safePercent = percent.coerceIn(0, 100)
    val color = when {
        safePercent >= 80 -> MaterialTheme.colorScheme.error
        safePercent >= 65 -> MaterialTheme.colorScheme.tertiary
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
        androidx.compose.material3.LinearProgressIndicator(
            progress = { safePercent / 100f },
            modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$safePercent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Text(
            " · ${formatTokenCountShared(estimatedTokens)}/${formatTokenCountShared(windowTokens)}" +
                if (compactions > 0) " · $compactions compacted" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatTokenCountShared(value: Int): String = when {
    value >= 1_000_000 -> "${(value / 100_000) / 10f}M"
    value >= 1_000 -> "${value / 1000}k"
    else -> value.toString()
}

@Composable
internal fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        modifier = Modifier.height(30.dp)
    )
}

@Composable
internal fun CompactAction(icon: ImageVector, label: String, visible: Boolean, onClick: () -> Unit) {
    if (!visible) return
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(icon, label, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun EmptyPane(icon: ImageVector, title: String, body: String) {
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

/* ======================================================================= */
/* Message bubbles                                                          */
/* ======================================================================= */

@Composable
internal fun UserBubble(message: AgentMessage) {
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
internal fun AssistantBubble(message: AgentMessage) {
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
internal fun MessageTime(timestamp: Long) {
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

internal val AgentMessage.isThought: Boolean
    get() = role == "assistant" && metaString("kind") == "thought"

internal val AgentMessage.isCommand: Boolean
    get() = role == "user" && metaString("kind") == "command"

private val AgentMessage.toolSucceeded: Boolean
    get() = metadata?.get("ok")?.jsonPrimitive?.booleanOrNull ?: true

private val AgentMessage.thoughtDurationMs: Long
    get() = metadata?.get("durationMs")?.jsonPrimitive?.longOrNull ?: 0L

/* ======================================================================= */
/* Model reasoning — collapsible "Thought for N seconds" (LMArena style)    */
/* ======================================================================= */

@Composable
internal fun ThoughtRow(message: AgentMessage) {
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
internal fun CommandRow(message: AgentMessage) {
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
internal fun ToolCallRow(message: AgentMessage) {
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
            val content = message.content
            val isDiff = content.lineSequence().any {
                it.startsWith("diff --git") || it.startsWith("@@") ||
                    (it.startsWith("+") && !it.startsWith("+++")) ||
                    (it.startsWith("-") && !it.startsWith("---"))
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp, bottom = 4.dp)
            ) {
                Column(Modifier.padding(8.dp)) {
                    ToolContentStats(content, verb.label)
                    if (isDiff) {
                        DiffView(content, Modifier.padding(top = 4.dp))
                    } else {
                        SelectionContainer {
                            Text(
                                content,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolContentStats(content: String, verbLabel: String) {
    val added = content.lineSequence().count { it.startsWith("+") && !it.startsWith("+++") }
    val removed = content.lineSequence().count { it.startsWith("-") && !it.startsWith("---") }
    val totalLines = content.lineSequence().count()
    val isReadLike = verbLabel == "Read" || verbLabel == "Listed dir" || verbLabel == "Searched"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (added > 0 || removed > 0) {
            if (added > 0) {
                Text(
                    "+$added",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4CAF50)
                )
            }
            if (removed > 0) {
                Text(
                    "-$removed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFF44336)
                )
            }
        } else if (isReadLike && totalLines > 0) {
            Text(
                "$totalLines lines",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
