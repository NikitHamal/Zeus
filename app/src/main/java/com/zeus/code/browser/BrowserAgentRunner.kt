package com.zeus.code.browser

import android.content.Context
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.SecureTokenStore
import java.util.UUID
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

    fun sendMessage(userPrompt: String) {
        if (userPrompt.isBlank() || _isRunning.value) return
        val userMsg = WebAgentMessage(role = "user", content = userPrompt.trim())
        _messages.value = _messages.value + userMsg
        _isRunning.value = true

        agentJob = scope.launch {
            try {
                runAutonomousLoop(userPrompt)
            } catch (e: Exception) {
                _currentStatus.value = "Error: ${e.message}"
                _messages.value = _messages.value + WebAgentMessage(
                    role = "assistant",
                    content = "Task encountered an error: ${e.message}"
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

    private suspend fun runAutonomousLoop(goal: String) = withContext(Dispatchers.IO) {
        val token = tokenStore.read()
        val currentModel = _selectedModel.value
        val maxIterations = 20

        for (iteration in 1..maxIterations) {
            if (!_isRunning.value) break
            _currentStatus.value = "Inspecting page..."

            val pageContent = browserController.extractPageContent()
            val prompt = buildPrompt(goal, pageContent, _messages.value)

            _currentStatus.value = "Reasoning with ${currentModel.label}..."
            val rawResponse = callModel(prompt, currentModel, token)

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
You are an Autonomous Mobile Web Agent. You control a browser on an Android device to complete user requests.

Goal: "$goal"
Current URL: "${page.url}"
Page Title: "${page.title}"

Interactive DOM Elements:
$elements

Recent Action History:
$recentHistory

Respond with EXACTLY ONE JSON object:
{
  "thought": "Your concise step-by-step reasoning",
  "action": "navigate" | "click" | "type" | "extract" | "done",
  "target": "URL for navigate, CSS selector for click/type, or final summary for done",
  "text": "text to type if action is type"
}
        """.trimIndent()
    }

    private suspend fun callModel(prompt: String, option: LlmModelOption, token: String?): String {
        return try {
            if (token != null) {
                val res = api.testLlmProvider(
                    token = token,
                    fields = mapOf(
                        "prompt" to prompt,
                        "provider" to option.provider,
                        "model" to option.model,
                        "system" to "You are an autonomous mobile browser agent. Always return valid JSON."
                    )
                )
                if (res.reply.isNotBlank()) res.reply else "{\"thought\": \"Inspecting page content\", \"action\": \"extract\", \"target\": \"\"}"
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
