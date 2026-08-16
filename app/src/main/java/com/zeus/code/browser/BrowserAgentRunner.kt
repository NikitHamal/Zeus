package com.zeus.code.browser

import android.content.Context
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.SecureTokenStore
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

data class BrowserAgentStep(
    val iteration: Int,
    val thought: String,
    val actionName: String,
    val actionTarget: String,
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
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

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentGoal = MutableStateFlow("")
    val currentGoal: StateFlow<String> = _currentGoal.asStateFlow()

    private val _steps = MutableStateFlow<List<BrowserAgentStep>>(emptyList())
    val steps: StateFlow<List<BrowserAgentStep>> = _steps.asStateFlow()

    private val _statusText = MutableStateFlow("Idle")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun startTask(goal: String, maxSteps: Int = 15) {
        if (goal.isBlank() || _isRunning.value) return
        _currentGoal.value = goal
        _steps.value = emptyList()
        _isRunning.value = true
        _statusText.value = "Starting autonomous browser agent..."

        agentJob = scope.launch {
            try {
                runLoop(goal, maxSteps)
            } catch (e: Exception) {
                _statusText.value = "Agent error: ${e.message}"
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stopTask() {
        agentJob?.cancel()
        _isRunning.value = false
        _statusText.value = "Stopped by user"
    }

    private suspend fun runLoop(goal: String, maxSteps: Int) = withContext(Dispatchers.IO) {
        val token = tokenStore.read()
        for (stepNum in 1..maxSteps) {
            if (!_isRunning.value) break
            _statusText.value = "Step $stepNum/$maxSteps: Reading page DOM..."

            val pageContent = browserController.extractPageContent()
            val prompt = buildAgentPrompt(goal, pageContent, _steps.value)

            _statusText.value = "Step $stepNum/$maxSteps: Reasoning with cloud model..."
            val modelResponse = callCloudModel(prompt, token)

            val parsedAction = parseModelAction(modelResponse)
            _statusText.value = "Step $stepNum/$maxSteps: Executing ${parsedAction.name} on device..."

            val stepResult = executeOnDeviceAction(parsedAction)

            val stepRecord = BrowserAgentStep(
                iteration = stepNum,
                thought = parsedAction.thought,
                actionName = parsedAction.name,
                actionTarget = parsedAction.target,
                result = stepResult
            )
            _steps.value = _steps.value + stepRecord

            if (parsedAction.name.equals("done", ignoreCase = true) || parsedAction.name.equals("finish", ignoreCase = true)) {
                _statusText.value = "Goal Accomplished: ${parsedAction.thought}"
                break
            }
        }
    }

    private fun buildAgentPrompt(goal: String, page: BrowserPageContent, pastSteps: List<BrowserAgentStep>): String {
        val elementsText = page.elements.take(40).joinToString("\n") { el ->
            "- [${el.tagName}] selector: `${el.selector}` | text: \"${el.text}\" ${if (el.href.isNotBlank()) "(href: ${el.href})" else ""}"
        }

        val historyText = if (pastSteps.isNotEmpty()) {
            pastSteps.joinToString("\n") { s ->
                "Step ${s.iteration}: Action: ${s.actionName} (${s.actionTarget}) -> Result: ${s.result}"
            }
        } else "None yet."

        return """
You are an Autonomous Mobile Browser Agent running locally on an Android device.
You have full control over the mobile browser to fulfill the user's goal.

User Goal: "$goal"
Current Page Title: "${page.title}"
Current URL: "${page.url}"

Interactive Elements on Current Viewport:
$elementsText

Past Action History:
$historyText

Choose EXACTLY ONE next action to execute on device.
Respond in JSON format:
{
  "thought": "Brief explanation of what you are trying to do next",
  "action": "navigate" | "click" | "type" | "extract" | "done",
  "target": "URL for navigate, selector for click/type, or final answer for done",
  "text": "text to type if action is type"
}
        """.trimIndent()
    }

    private suspend fun callCloudModel(prompt: String, token: String?): String {
        return try {
            if (token != null) {
                val res = api.testLlmProvider(
                    token = token,
                    fields = mapOf(
                        "prompt" to prompt,
                        "system" to "You are an autonomous browser agent. Always return valid JSON."
                    )
                )
                if (res.reply.isNotBlank()) res.reply else "{\"thought\": \"Analyze page\", \"action\": \"extract\", \"target\": \"\"}"
            } else {
                "{\"thought\": \"No token configured\", \"action\": \"done\", \"target\": \"Please log in to use cloud models.\"}"
            }
        } catch (e: Exception) {
            "{\"thought\": \"Model call failed: ${e.message}\", \"action\": \"done\", \"target\": \"Failed to reach model\"}"
        }
    }

    private fun parseModelAction(raw: String): ParsedAction {
        val cleaned = raw.substringAfter("{").substringBeforeLast("}").let { "{$it}" }
        return try {
            val jsonElement = json.parseToJsonElement(cleaned) as? JsonObject
            val thought = (jsonElement?.get("thought") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val action = (jsonElement?.get("action") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "extract"
            val target = (jsonElement?.get("target") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val text = (jsonElement?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            ParsedAction(thought, action, target, text)
        } catch (_: Exception) {
            ParsedAction("Extracted page info", "extract", "", "")
        }
    }

    private suspend fun executeOnDeviceAction(action: ParsedAction): String {
        return when (action.name.lowercase()) {
            "navigate" -> {
                val res = browserController.navigate(action.target)
                res.message
            }
            "click" -> {
                val res = browserController.clickElement(action.target)
                res.message
            }
            "type" -> {
                val res = browserController.typeText(action.target, action.text)
                res.message
            }
            "extract" -> {
                val content = browserController.extractPageContent()
                "Extracted ${content.elements.size} elements. Page title: ${content.title}"
            }
            "done", "finish" -> {
                "Task completed: ${action.target}"
            }
            else -> "Unknown action: ${action.name}"
        }
    }

    data class ParsedAction(
        val thought: String,
        val name: String,
        val target: String,
        val text: String
    )
}
