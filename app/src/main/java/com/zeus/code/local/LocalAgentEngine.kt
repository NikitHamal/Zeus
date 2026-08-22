package com.zeus.code.local

import java.io.File

/**
 * The Local Mode coding-agent loop.
 *
 * For every step it asks the chosen model for the next move, executes the
 * requested tools inside the workspace sandbox, feeds results back and keeps
 * going until the model calls [LocalAgentTools.FINISH], stops requesting
 * tools or the task is cancelled/stopped externally. There is no step cap —
 * long tasks are bounded only by context compaction and the user's stop.
 */
class LocalAgentEngine(
    private val llm: LocalLlmClient,
    private val onEvent: suspend (kind: String, text: String) -> Unit,
    private val shouldStop: () -> Boolean = { false }
) {

    /**
     * Runs the loop to completion. Returns the final summary (may be blank on
     * failure). Never throws for ordinary failures — those are reported as
     * events and end the run with a failed outcome handled by the caller.
     */
    suspend fun run(
        task: LocalTask,
        workspace: File,
        onStep: suspend (step: Int, changedFiles: Set<String>) -> Unit,
        /** Live view of the stored task so mid-run guidance is picked up. */
        latest: () -> LocalTask? = { null }
    ): EngineOutcome {
        val tools = LocalAgentTools(workspace)
        val definitions = tools.definitions()
        val system = buildSystemPrompt(task, workspace)

        // Transcript of user/assistant/tool turns replayed to the provider.
        val history = mutableListOf<LocalMessage>(LocalMessage(LocalRole.USER, buildUserBrief(task)))
        val changedFiles = linkedSetOf<String>()
        var lastError: String? = null
        var summary = ""

        appendEvent(LocalEventKind.INFO, "Task started with ${task.choice.label.ifBlank { task.choice.model }}.")

        var step = 0
        while (true) {
            step += 1
            if (shouldStop()) return stopped(changedFiles)
            compactHistory(history)
            onStep(step - 1, changedFiles)

            // Guidance sent while running reaches the model before the next move.
            absorbLateGuidance(latest() ?: task, history)

            val reply = try {
                llm.complete(task.choice, system, history.toList(), definitions)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error.message ?: error.javaClass.simpleName
                appendEvent(LocalEventKind.ERROR, "Model call failed: $lastError")
                val prefix = if (step > 1) "Interrupted at step $step: " else ""
                return EngineOutcome(
                    successful = false,
                    summary = "",
                    changedFiles = changedFiles,
                    error = "$prefix$lastError"
                )
            }

            history += LocalMessage(LocalRole.ASSISTANT, reply.content, hasToolCalls = reply.wantsTools)
            if (reply.content.isNotBlank()) {
                appendEvent(LocalEventKind.LLM, reply.content.take(2000))
            }

            if (!reply.wantsTools) {
                summary = reply.content.trim()
                appendEvent(LocalEventKind.DONE, "Agent finished after $step step(s).")
                return EngineOutcome(successful = true, summary = summary, changedFiles = changedFiles, error = null)
            }

            for (call in reply.toolCalls) {
                if (shouldStop()) return stopped(changedFiles)

                if (call.name == LocalAgentTools.FINISH) {
                    val args = runCatching { org.json.JSONObject(call.argumentsJson) }.getOrNull()
                    val declaredSummary = args?.optString("summary")
                        ?.takeUnless { it.isBlank() || it == "null" }
                        .orEmpty()
                    summary = declaredSummary.ifBlank {
                        reply.content.trim().ifBlank { "Task finished." }
                    }
                    appendEvent(LocalEventKind.DONE, "Agent finished after $step step(s).")
                    return EngineOutcome(successful = true, summary = summary, changedFiles = changedFiles, error = null)
                }

                val shortArgs = summariseArgs(call.argumentsJson)
                appendEvent(LocalEventKind.TOOL, "${call.name}($shortArgs)")
                val result = try {
                    tools.execute(call.name, call.argumentsJson)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    "ERROR: ${error.message ?: error.javaClass.simpleName}"
                }
                if (tools.isMutating(call.name)) trackChangedFiles(result, changedFiles)
                appendEvent(
                    if (result.startsWith("ERROR:")) LocalEventKind.ERROR else LocalEventKind.FILE,
                    result.take(1200)
                )
                history += LocalMessage(
                    role = LocalRole.TOOL,
                    content = result,
                    toolCallId = call.id,
                    toolName = call.name
                )
            }

            onStep(step, changedFiles)
        }
    }

    private suspend fun stopped(changedFiles: Set<String>): EngineOutcome {
        appendEvent(LocalEventKind.INFO, "Task stopped by user.")
        return EngineOutcome(successful = false, summary = "", changedFiles = changedFiles, error = "stopped")
    }

    /**
     * User guidance appended to the task while it runs (from the session
     * composer) is folded into the conversation before the next model call.
     */
    private var deliveredGuidance = 0

    private suspend fun absorbLateGuidance(task: LocalTask, history: MutableList<LocalMessage>) {
        val brief = buildUserBrief(task)
        val pending = task.messages.filter { it.role == LocalRole.USER && it.content != brief }
        while (deliveredGuidance < pending.size) {
            val message = pending[deliveredGuidance]
            deliveredGuidance += 1
            history += LocalMessage(LocalRole.USER, message.content)
            appendEvent(LocalEventKind.INFO, "Guidance received: ${message.content.take(300)}")
        }
    }

    /**
     * Keeps long tasks inside a sane context budget by shrinking old tool
     * outputs once the transcript grows past [MAX_HISTORY_CHARS]. The most
     * recent turns are always preserved verbatim.
     */
    private fun compactHistory(history: MutableList<LocalMessage>) {
        var total = history.sumOf { it.content.length }
        if (total <= MAX_HISTORY_CHARS) return
        for (index in history.indices) {
            if (total <= MAX_HISTORY_CHARS) return
            val message = history[index]
            if (message.role == LocalRole.TOOL && message.content.length > 800) {
                val trimmed = message.content.take(400) +
                    "\n…[older tool output truncated to save context]"
                total -= message.content.length - trimmed.length
                history[index] = message.copy(content = trimmed)
            }
        }
    }

    private suspend fun appendEvent(kind: String, text: String) {
        onEvent(kind, text)
    }

    private fun trackChangedFiles(toolResult: String, into: MutableSet<String>) {
        Regex("""'([^']+)'""").findAll(toolResult).forEach { match ->
            val candidate = match.groupValues[1]
            if (candidate.contains('.') || candidate.endsWith("/")) {
                into += candidate.trimEnd('/')
            }
        }
    }

    private fun summariseArgs(json: String): String = runCatching {
        val obj = org.json.JSONObject(json)
        obj.keys().asSequence().take(3).joinToString(", ") { key ->
            val value = obj.opt(key)?.toString().orEmpty()
            "$key=${value.take(60)}"
        }
    }.getOrDefault(json.take(80))

    // ------------------------------------------------------------------
    // Prompting
    // ------------------------------------------------------------------

    private fun buildSystemPrompt(task: LocalTask, workspace: File): String = buildString {
        appendLine("You are Zeus Local Agent, an autonomous coding agent running directly on the user's Android device.")
        appendLine("You work inside exactly one workspace directory; you cannot access anything outside it.")
        appendLine()
        appendLine("WORKSPACE: ${workspace.name}")
        appendLine()
        appendLine("## Rules")
        appendLine("- Inspect before you change: list/read files first, then edit.")
        appendLine("- Prefer edit_file with a unique exact snippet over rewriting whole files.")
        appendLine("- After meaningful milestones inside a git workspace, create a commit with git_commit.")
        appendLine("- Verify your work when possible (read back edited files, run quick commands).")
        appendLine("- Keep responses terse. No markdown code fences around tool calls.")
        appendLine("- When the goal is fully achieved, call finish with a short summary of changes.")
        appendLine()
        appendLine("## Tool calling protocol")
        appendLine("To invoke a tool, output one block per call, nothing else inside it:")
        appendLine("${TextProtocol.OPEN}")
        appendLine("""{"tool": "TOOL_NAME", "args": { ... }}""")
        appendLine(TextProtocol.CLOSE)
        appendLine("You may emit multiple blocks in sequence. Available tools:")
        appendLine(LocalAgentTools.NAMES.joinToString())
        appendLine()
        appendLine("Tool notes:")
        appendLine("- list_files(path, depth) → tree listing")
        appendLine("- read_file(path, startLine?, endLine?) → numbered lines")
        appendLine("- write_file(path, content) → full overwrite")
        appendLine("- edit_file(path, find, replace) → exact single-match replacement")
        appendLine("- delete_path(path)")
        appendLine("- search_files(query, path?, regex?) → path:line matches")
        appendLine("- run_command(command) → /system/bin/sh output (no sudo, no interactive input)")
        appendLine("- git_status() / git_diff() / git_commit(message)")
        appendLine("- finish(summary)")
        appendLine()
        appendLine("If a tool returns ERROR:, fix the cause and retry differently. Never repeat a failing call unchanged more than twice.")
    }

    private fun buildUserBrief(task: LocalTask): String =
        "GOAL: ${task.goal.trim()}"

    private companion object {
        const val MAX_HISTORY_CHARS = 160_000
    }

    data class EngineOutcome(
        val successful: Boolean,
        val summary: String,
        val changedFiles: Set<String>,
        val error: String?
    )
}
