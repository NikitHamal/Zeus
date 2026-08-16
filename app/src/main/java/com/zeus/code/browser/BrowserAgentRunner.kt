package com.zeus.code.browser

import android.content.Context
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.SecureTokenStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

data class WebAgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "action"
    val content: String,
    val thought: String = "",
    val actionName: String = "",
    val actionTarget: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class LlmModelOption(
    val provider: String,
    val model: String,
    val label: String
)

class BrowserAgentRunner(
    private val context: Context,
    private val browserController: BrowserController,
    private val api: BackgroundAgentApi = BackgroundAgentApi(context),
    private val tokenStore: SecureTokenStore = SecureTokenStore(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var agentJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    val availableModels = listOf(
        LlmModelOption("motiftech", "motif-102b", "Motif 3 (chat.motiftech.io)"),
        LlmModelOption("motiftech", "motif-12-7b-reasoning", "Motif 12.7B Reasoning"),
        LlmModelOption("k2think", "MBZUAI-IFM/K2-Think-v2", "K2 Think V2 (Reasoning)"),
        LlmModelOption("poolside", "laguna-s-2.1", "Poolside Laguna S 2.1"),
        LlmModelOption("qwen", "qwen-plus", "Qwen Plus (NEBians)"),
        LlmModelOption("qwen", "qwen-max", "Qwen Max"),
        LlmModelOption("openai", "gpt-4o", "OpenAI GPT-4o"),
        LlmModelOption("openai", "gpt-4o-mini", "OpenAI GPT-4o Mini"),
        LlmModelOption("gemini", "gemini-2.5-flash", "Google Gemini 2.5 Flash"),
        LlmModelOption("gemini", "gemini-2.5-pro", "Google Gemini 2.5 Pro"),
        LlmModelOption("anthropic", "claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
        LlmModelOption("deepseek", "deepseek-coder", "DeepSeek Coder")
    )

    private val _selectedModel = MutableStateFlow(availableModels[0])
    val selectedModel: StateFlow<LlmModelOption> = _selectedModel.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _messages = MutableStateFlow<List<WebAgentMessage>>(emptyList())
    val messages: StateFlow<List<WebAgentMessage>> = _messages.asStateFlow()

    private val _currentStatus = MutableStateFlow("Ready")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    fun selectModel(option: LlmModelOption) {
        _selectedModel.value = option
    }

    fun startTask(
        goal: String,
        provider: String = "motiftech",
        model: String = "motif-102b",
        providerId: String = ""
    ) {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentStatus.value = "Starting browser agent..."
        _messages.value = _messages.value + WebAgentMessage(role = "user", content = goal)

        agentJob = scope.launch {
            try {
                runAutonomousLoop(goal, provider, model, providerId)
            } catch (e: CancellationException) {
                _currentStatus.value = "Cancelled"
            } catch (e: Exception) {
                _messages.value = _messages.value + WebAgentMessage(
                    role = "assistant",
                    content = "Error during execution: ${e.message}"
                )
            } finally {
                _isRunning.value = false
                _currentStatus.value = "Ready"
            }
        }
    }

    fun stop() {
        agentJob?.cancel()
        _isRunning.value = false
        _currentStatus.value = "Stopped"
        _messages.value = _messages.value + WebAgentMessage(
            role = "assistant",
            content = "Execution stopped by user."
        )
    }

    private suspend fun runAutonomousLoop(
        goal: String,
        provider: String,
        model: String,
        providerId: String
    ) = withContext(Dispatchers.IO) {
        val token = tokenStore.get()
        val maxIterations = 20

        for (iteration in 1..maxIterations) {
            if (!_isRunning.value) break
            _currentStatus.value = "Inspecting page..."

            val pageContent = browserController.extractPageContent()
            val prompt = buildPrompt(goal, pageContent, _messages.value)

            _currentStatus.value = "Reasoning with $model..."
            val rawResponse = callModel(prompt, provider, model, providerId, token)

            val parsed = parseAction(rawResponse)

            if (parsed.thought.isNotBlank()) {
                _currentStatus.value = parsed.thought
            }

            if (parsed.name.equals("done", ignoreCase = true) || parsed.name.equals("finish", ignoreCase = true)) {
                _messages.value = _messages.value + WebAgentMessage(
                    role = "assistant",
                    content = if (parsed.target.isNotBlank()) parsed.target else parsed.thought,
                    thought = parsed.thought
                )
                break
            }

            _currentStatus.value = "Executing ${parsed.name} on device..."
            val actionResult = executeAction(parsed)

            _messages.value = _messages.value + WebAgentMessage(
                role = "action",
                content = actionResult,
                thought = parsed.thought,
                actionName = parsed.name,
                actionTarget = parsed.target
            )
        }
    }

    private fun buildPrompt(goal: String, page: BrowserPageContent, history: List<WebAgentMessage>): String {
        val elements = page.elements.take(35).joinToString("\n") { el ->
            "- [${el.tagName}] selector: `${el.selector}` | text: \"${el.text}\" ${if (el.href.isNotBlank()) "(href: ${el.href})" else ""}"
        }

        val recentHistory = history.takeLast(6).joinToString("\n") { m ->
            when (m.role) {
                "user" -> "User: ${m.content}"
                "action" -> "Action: ${m.actionName} (${m.actionTarget}) -> Result: ${m.content}"
                else -> "Assistant: ${m.content}"
            }
        }

        return """
You are an autonomous AI browser controller agent running on an Android device.
User Goal: "$goal"

Current Page:
Title: ${page.title}
URL: ${page.url}
Visible interactive elements:
$elements

Recent Actions:
$recentHistory

Determine the single next action. Return ONLY a JSON object with:
{
  "thought": "brief explanation of what you are doing",
  "action": "navigate" | "click" | "type" | "extract" | "done",
  "target": "URL for navigate, CSS selector for click/type, or final summary for done",
  "text": "text to type if action is type"
}
        """.trimIndent()
    }

    private suspend fun callModel(
        prompt: String,
        provider: String,
        model: String,
        providerId: String,
        token: String?
    ): String {
        return try {
            if (token != null) {
                val res = api.chat(
                    token = token,
                    provider = provider,
                    model = model,
                    providerId = providerId,
                    prompt = prompt,
                    system = "You are an autonomous mobile browser agent. Always return valid JSON."
                )
                if (res.ok && res.reply.isNotBlank()) res.reply else "{\"thought\": \"Inspecting page content\", \"action\": \"extract\", \"target\": \"\"}"
            } else {
                "{\"thought\": \"Authorization required\", \"action\": \"done\", \"target\": \"Please sign in to Zeus to run cloud AI models.\"}"
            }
        } catch (e: Exception) {
            "{\"thought\": \"Error contacting AI model: ${e.message}\", \"action\": \"done\", \"target\": \"Model inference failed\"}"
        }
    }

    private fun parseAction(raw: String): ParsedAction {
        val cleaned = raw.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
        return try {
            val jsonElement = json.parseToJsonElement(cleaned) as? JsonObject
            val thought = (jsonElement?.get("thought") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val action = (jsonElement?.get("action") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "extract"
            val target = (jsonElement?.get("target") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val text = (jsonElement?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            ParsedAction(thought, action, target, text)
        } catch (_: Exception) {
            ParsedAction("Extracted page elements", "extract", "", "")
        }
    }

    private suspend fun executeAction(action: ParsedAction): String {
        return when (action.name.lowercase()) {
            "navigate" -> browserController.navigate(action.target).message
            "click" -> browserController.clickElement(action.target).message
            "type" -> browserController.typeText(action.target, action.text).message
            "extract" -> {
                val content = browserController.extractPageContent()
                "Extracted ${content.elements.size} elements from ${content.title}"
            }
            else -> "Executed: ${action.name}"
        }
    }

    data class ParsedAction(
        val thought: String,
        val name: String,
        val target: String,
        val text: String
    )
}
