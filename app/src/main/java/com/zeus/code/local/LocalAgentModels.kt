package com.zeus.code.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* ------------------------------------------------------------------------- */
/* Provider model choices                                                    */
/* ------------------------------------------------------------------------- */

/** Where a local-mode LLM call is served from. */
object LocalSource {
    const val NEBIANS = "nebians"
    const val ZEN = "zen"
    const val CUSTOM = "custom"
}

/** One pickable model inside the local mode picker. */
@Serializable
data class LocalModelChoice(
    /** [LocalSource.NEBANS]… see constants above. */
    val source: String = "",
    /** Model id sent to the endpoint, e.g. `code-supernova` or `qwen3.7-plus`. */
    val model: String = "",
    /** Human label shown in the picker, e.g. "OpenCode Zen · code-supernova". */
    val label: String = "",
    /** NEBians catalog slug (`qwen`, `agnes`, `openai`, ...). NEBians only. */
    val nebSlug: String = "",
    /** NEBians BYOK/custom row id for the saved key. NEBians only. */
    val nebRowId: String = "",
    /** Custom provider config id on this device. Custom only. */
    val customId: String = ""
) {
    val isValid: Boolean get() = source.isNotBlank() && model.isNotBlank()

    fun matches(other: LocalModelChoice): Boolean =
        source == other.source && model.equals(other.model, true) &&
            nebSlug == other.nebSlug && nebRowId == other.nebRowId && customId == other.customId
}

/* ------------------------------------------------------------------------- */
/* Conversation + events                                                     */
/* ------------------------------------------------------------------------- */

object LocalRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
}

/** A single transcript entry kept per task (never the raw system prompt). */
@Serializable
data class LocalMessage(
    val role: String,
    val content: String,
    /** Tool invocation id when [role] is [LocalRole.TOOL]. */
    val toolCallId: String = "",
    val toolName: String = "",
    /** True when this assistant turn carried tool invocations. */
    val hasToolCalls: Boolean = false
)

object LocalEventKind {
    const val INFO = "info"
    const val LLM = "llm"
    const val TOOL = "tool"
    const val FILE = "file"
    const val ERROR = "error"
    const val DONE = "done"
}

@Serializable
data class LocalEvent(
    val id: Long = 0L,
    val at: Long = 0L,
    val kind: String = LocalEventKind.INFO,
    val text: String = ""
)

/* ------------------------------------------------------------------------- */
/* Tasks                                                                     */
/* ------------------------------------------------------------------------- */

object LocalTaskStatus {
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val STOPPED = "stopped"

    fun isActive(status: String): Boolean = status == QUEUED || status == RUNNING
}

@Serializable
data class LocalTask(
    val id: String = "",
    val workspaceName: String = "",
    val workspacePath: String = "",
    val goal: String = "",
    val status: String = LocalTaskStatus.QUEUED,
    val choice: LocalModelChoice = LocalModelChoice(),
    /** Legacy step budget; `0` (and any value) is ignored — the loop is unlimited. */
    val maxSteps: Int = 0,
    val steps: Int = 0,
    val events: List<LocalEvent> = emptyList(),
    val messages: List<LocalMessage> = emptyList(),
    val changedFiles: List<String> = emptyList(),
    val summary: String = "",
    val error: String = "",
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long = 0L,
    /** Git branch created for this task when the workspace is a repository. */
    val workBranch: String = ""
) {
    val isActive: Boolean get() = LocalTaskStatus.isActive(status)
    val progressLabel: String
        get() = when (status) {
            LocalTaskStatus.QUEUED -> "Queued"
            LocalTaskStatus.RUNNING -> if (steps > 0) "Step $steps" else "Starting"
            LocalTaskStatus.COMPLETED -> "Completed"
            LocalTaskStatus.FAILED -> "Failed"
            else -> "Stopped"
        }
}

/* ------------------------------------------------------------------------- */
/* Engine output types                                                       */
/* ------------------------------------------------------------------------- */

/** A tool invocation requested by the model (parsed from native or text form). */
data class LocalToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

/** Tool definition advertised to OpenAI-compatible endpoints. */
data class LocalToolDef(
    val name: String,
    val description: String,
    /** JSON schema object for the `arguments` parameter. */
    val parameters: JsonObject
)
