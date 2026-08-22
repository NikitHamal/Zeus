package com.zeus.code.ui.agent

import com.zeus.code.local.LocalEventKind
import com.zeus.code.local.LocalTask
import com.zeus.code.model.AgentContext
import com.zeus.code.model.AgentMessage
import com.zeus.code.model.AgentSession
import com.zeus.code.model.AgentSessionLlm
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/* ======================================================================= */
/* Bridge — renders on-device tasks through the cloud session pipeline      */
/* ======================================================================= */

/** Every Local Mode task id starts with this prefix ([com.zeus.code.local.LocalTaskStore.newId]). */
const val LOCAL_ID_PREFIX = "lt_"

fun isLocalSessionId(id: String): Boolean = id.startsWith(LOCAL_ID_PREFIX)

/**
 * Projects a [LocalTask] onto [AgentSession] so the Background Agent screens
 * (dashboard cards, session chat, changes, deliver) render both modes with
 * one visual language and one set of actions.
 */
fun LocalTask.toAgentSession(): AgentSession {
    val messages = localTaskMessages(this)
    val transcriptChars = messages.sumOf { it.content.length } + events.sumOf { it.text.length }
    val estimatedTokens = transcriptChars / 4
    val windowTokens = 160_000
    return AgentSession(
        id = id,
        repoFullName = workspaceName,
        title = goal.lineSequence().firstOrNull().orEmpty().ifBlank { "Local task" },
        goal = goal,
        sourceBranch = workBranch.ifBlank { "local" },
        workBranch = workBranch,
        status = status,
        progress = if (maxSteps > 0) ((steps * 100) / maxSteps).coerceIn(0, 100) else 0,
        progressLabel = progressLabel,
        iteration = steps,
        summary = summary,
        lastError = error,
        createdAt = createdAt,
        startedAt = startedAt,
        updatedAt = updatedAt,
        completedAt = completedAt,
        context = AgentContext(
            estimatedTokens = estimatedTokens,
            windowTokens = windowTokens,
            percent = if (windowTokens > 0) (estimatedTokens * 100 / windowTokens).coerceIn(0, 100) else 0,
            compactions = 0
        ),
        llm = AgentSessionLlm(
            provider = choice.source,
            model = choice.model,
            label = choice.label.ifBlank { choice.model }
        ),
        changedFiles = changedFiles,
        messages = messages
    )
}

/**
 * Maps the durable local-event log onto the same chat model the cloud agent
 * renders, so both surfaces share one visual language:
 *
 *  - the goal opens the transcript as a user bubble
 *  - `TOOL` events pair with their following result (`FILE` ok / `ERROR` fail)
 *    and render as collapsible tool rows with diff detection
 *  - model commentary, guidance notes, completion notes and the final summary
 *    are assistant bubbles with markdown
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
                        id = "${task.id}-pair-${out.size}",
                        role = "tool",
                        label = label,
                        content = event.text,
                        metadata = toolMetadata(ok = !event.text.startsWith("ERROR:")),
                        createdAt = pendingToolAt
                    )
                    pendingToolLabel = null
                } else {
                    out += AgentMessage(
                        id = "${task.id}-file-${out.size}",
                        role = "tool",
                        label = "Workspace update",
                        content = event.text,
                        metadata = toolMetadata(ok = true),
                        createdAt = event.at
                    )
                }
            }
            LocalEventKind.ERROR -> {
                val label = pendingToolLabel
                if (label != null) {
                    out += AgentMessage(
                        id = "${task.id}-pair-${out.size}",
                        role = "tool",
                        label = label,
                        content = event.text,
                        metadata = toolMetadata(ok = false),
                        createdAt = pendingToolAt
                    )
                    pendingToolLabel = null
                } else {
                    out += AgentMessage(
                        id = "${task.id}-err-${out.size}",
                        role = "assistant",
                        content = "**Error:** ${event.text}",
                        createdAt = event.at
                    )
                }
            }
            else -> out += AgentMessage(
                id = "${task.id}-evt-${event.id}-${out.size}",
                role = "assistant",
                content = event.text,
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

private fun toolMetadata(ok: Boolean) = buildJsonObject { put("ok", ok) }
