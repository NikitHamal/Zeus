package com.zeus.code.browser

import android.content.Context
import com.zeus.code.data.AgentLlmToolParser
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.BackgroundAgentApiException
import com.zeus.code.data.ParsedLlmAction
import com.zeus.code.data.SecureTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class WebAgentMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user", "assistant", "action", "system"
    val content: String,
    val thought: String = "",
    val actionName: String = "",
    val actionTarget: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class BrowserAgentRunner(
    private val context: Context,
    val browserController: BrowserController,
    private val api: BackgroundAgentApi = BackgroundAgentApi(context),
    private val tokenStore: SecureTokenStore = SecureTokenStore(context, "background_agent")
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var agentJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentStatus = MutableStateFlow("Ready")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val _messages = MutableStateFlow<List<WebAgentMessage>>(emptyList())
    val messages: StateFlow<List<WebAgentMessage>> = _messages.asStateFlow()

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun startTask(
        goal: String,
        provider: String = "qwen",
        model: String = "",
        providerId: String = ""
    ) {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentStatus.value = "Starting autonomous web agent..."

        _messages.value = _messages.value + WebAgentMessage(
            role = "user",
            content = goal
        )

        agentJob = scope.launch {
            try {
                runAutonomousLoop(goal, provider, model, providerId)
            } catch (e: CancellationException) {
                _currentStatus.value = "Stopped"
            } catch (e: Throwable) {
                _messages.value = _messages.value + WebAgentMessage(
                    role = "system",
                    content = "Error during execution: ${e.message ?: e.javaClass.simpleName}"
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
            role = "system",
            content = "Execution stopped by user."
        )
    }

    private suspend fun runAutonomousLoop(
        goal: String,
        provider: String,
        model: String,
        providerId: String
    ) = withContext(Dispatchers.IO) {
        val token = tokenStore.read().orEmpty()
        val maxIterations = 20
        val history = mutableListOf<String>()

        for (iteration in 1..maxIterations) {
            if (!_isRunning.value) break

            _currentStatus.value = "Inspecting page DOM..."
            val pageContent = browserController.extractPageContent()
            val pageSummary = pageContent.toPromptSummary(maxElements = 35)

            val systemPrompt = """
You are an expert Autonomous Mobile Web Agent.
Your mission is to accomplish the user's objective by browsing websites, clicking elements, submitting forms, scrolling, and extracting information.

Available Actions (Respond with JSON or XML tool call):
1. {"action": "navigate", "target": "https://example.com", "reason": "Go to URL or search query"}
2. {"action": "click", "target": "z-3", "reason": "Click interactive element by ID or CSS selector"}
3. {"action": "type", "target": "z-1", "text": "Search term", "reason": "Type into input and submit"}
4. {"action": "scroll_down", "reason": "Scroll down to see more results"}
5. {"action": "scroll_up", "reason": "Scroll up"}
6. {"action": "extract", "reason": "Extract text content from current page"}
7. {"action": "go_back", "reason": "Navigate back"}
8. {"action": "reload", "reason": "Reload current page"}
9. {"action": "wait", "seconds": 2, "reason": "Wait for page update"}
10. {"action": "done", "text": "Final answer or summary of findings"}

Rules:
- Target elements using the [z-X] identifier from the interactive elements list.
- When you have answered or completed the goal, use action "done" with your complete findings in "text".
- Output valid JSON or XML <tool name="..."> format.
""".trimIndent()

            val prompt = """
User Goal: "$goal"
Step: $iteration / $maxIterations

Previous Steps:
${if (history.isEmpty()) "None (Starting)" else history.takeLast(5).joinToString("\n")}

Current Web Page:
$pageSummary

Determine the single next action.
""".trimIndent()

            val activeModelLabel = model.ifBlank { if (provider.isNotBlank()) provider else "NEBians Default" }
            _currentStatus.value = "Reasoning with $activeModelLabel..."
            val rawResponse = callLlm(
                token = token,
                provider = provider,
                model = model,
                providerId = providerId,
                system = systemPrompt,
                prompt = prompt
            )

            if (rawResponse == null) {
                break
            }

            // Parse response
            val parsedAction = AgentLlmToolParser.parseAction(rawResponse, defaultAction = "extract")
            val thought = parsedAction.thought.ifBlank { "Executing ${parsedAction.actionName}" }

            _currentStatus.value = thought

            if (parsedAction.actionName == "done" || parsedAction.actionName == "finish") {
                val answer = parsedAction.text.ifBlank { parsedAction.thought }.ifBlank { "Task completed." }
                _messages.value = _messages.value + WebAgentMessage(
                    role = "assistant",
                    content = answer,
                    thought = parsedAction.thought,
                    actionName = "done"
                )
                break
            }

            _currentStatus.value = "Executing ${parsedAction.actionName}..."
            val outcome = executeAction(parsedAction)
            history.add("Step $iteration: [${parsedAction.actionName.uppercase()}] ${parsedAction.thought} -> $outcome")

            _messages.value = _messages.value + WebAgentMessage(
                role = "action",
                content = outcome,
                thought = parsedAction.thought,
                actionName = parsedAction.actionName,
                actionTarget = parsedAction.target.ifBlank { parsedAction.text }
            )

            delay(1000)
        }
    }

    private suspend fun callLlm(
        token: String,
        provider: String,
        model: String,
        providerId: String,
        system: String,
        prompt: String
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (token.isBlank()) {
                    _messages.value = _messages.value + WebAgentMessage(
                        role = "system",
                        content = "⚠️ Device is not connected to NEBians. Please open the Agent tab to connect Zeus, or select a configured AI provider in Settings."
                    )
                    return@withContext null
                }

                val res = api.chat(
                    token = token,
                    provider = provider,
                    model = model,
                    providerId = providerId,
                    system = system,
                    prompt = prompt
                )

                if (!res.ok) {
                    val errMsg = res.error ?: "Inference request failed"
                    _messages.value = _messages.value + WebAgentMessage(
                        role = "system",
                        content = "⚠️ LLM Error: $errMsg. Please check your provider configuration or switch models."
                    )
                    return@withContext null
                }

                if (res.reply.isBlank()) {
                    _messages.value = _messages.value + WebAgentMessage(
                        role = "system",
                        content = "⚠️ Model returned an empty response. Please retry."
                    )
                    return@withContext null
                }

                res.reply
            } catch (e: BackgroundAgentApiException) {
                _messages.value = _messages.value + WebAgentMessage(
                    role = "system",
                    content = "⚠️ API Error (${e.statusCode}): ${e.message}"
                )
                null
            } catch (e: Exception) {
                _messages.value = _messages.value + WebAgentMessage(
                    role = "system",
                    content = "⚠️ Network error contacting LLM: ${e.message ?: e.javaClass.simpleName}"
                )
                null
            }
        }
    }

    private suspend fun executeAction(action: ParsedLlmAction): String {
        return withContext(Dispatchers.Main) {
            when (action.actionName) {
                "navigate", "goto", "open_url" -> {
                    val target = action.target.ifBlank { action.text }
                    browserController.navigate(target).message
                }
                "click" -> {
                    val target = action.target.ifBlank { action.text }
                    browserController.clickElement(target).message
                }
                "type" -> {
                    val target = action.target
                    val textToType = action.text
                    browserController.typeText(target, textToType, submit = true).message
                }
                "scroll_down", "scroll" -> {
                    browserController.scroll("down", 400).message
                }
                "scroll_up" -> {
                    browserController.scroll("up", 400).message
                }
                "go_back", "back" -> {
                    browserController.goBack().message
                }
                "reload" -> {
                    browserController.reload().message
                }
                "extract", "read_page" -> {
                    val content = browserController.extractPageContent()
                    "Extracted ${content.elements.size} elements from ${content.title}"
                }
                "wait" -> {
                    val waitMs = action.durationMs ?: 2000L
                    delay(waitMs)
                    "Waited ${waitMs / 1000}s"
                }
                else -> {
                    browserController.clickElement(action.target).message
                }
            }
        }
    }
}
