package com.zeus.code.automation

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

data class PhoneChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user", "agent", "system", "action"
    val text: String,
    val thought: String = "",
    val actionType: String? = null,
    val actionDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class PhoneAgentRunner(
    private val context: Context,
    val phoneController: PhoneController,
    val overlayManager: PhoneOverlayManager = PhoneOverlayManager(context),
    private val api: BackgroundAgentApi = BackgroundAgentApi(context),
    private val tokenStore: SecureTokenStore = SecureTokenStore(context, "background_agent")
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var agentJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentStatus = MutableStateFlow("Ready")
    val currentStatus: StateFlow<String> = _currentStatus.asStateFlow()

    private val _messages = MutableStateFlow<List<PhoneChatMessage>>(
        listOf(
            PhoneChatMessage(
                sender = "agent",
                text = "Hello! I am your Autonomous Phone Controller Agent. Tell me what to do on your device (e.g. 'Open Settings and check storage', 'Open YouTube and search for Lo-Fi', 'Open Douyin / TikTok and scroll 5 videos'). I will execute gestures and navigate in real-time."
            )
        )
    )
    val messages: StateFlow<List<PhoneChatMessage>> = _messages.asStateFlow()

    init {
        overlayManager.onPauseClicked = {
            togglePause()
        }
        overlayManager.onStopClicked = {
            stop()
        }
    }

    fun togglePause() {
        val paused = !_isPaused.value
        _isPaused.value = paused
        overlayManager.isPaused = paused
        _currentStatus.value = if (paused) "Paused" else "Running"
    }

    fun clearMessages() {
        _messages.value = listOf(
            PhoneChatMessage(
                sender = "agent",
                text = "Agent reset. Enter your next task whenever you're ready."
            )
        )
    }

    fun startTask(
        instruction: String,
        provider: String = "",
        model: String = "",
        providerId: String = ""
    ) {
        if (_isRunning.value) return

        if (!PhoneAutomationService.isConnected) {
            _messages.value = _messages.value + PhoneChatMessage(
                sender = "system",
                text = "⚠️ Accessibility Service is not active. Please enable 'Zeus' under Settings > Accessibility to allow touch/swipe gestures."
            )
            phoneController.openAccessibilitySettings()
            return
        }

        if (!overlayManager.hasOverlayPermission()) {
            _messages.value = _messages.value + PhoneChatMessage(
                sender = "system",
                text = "⚠️ 'Display over other apps' permission is required for the live floating status overlay."
            )
            overlayManager.requestOverlayPermission()
        }

        val token: String = tokenStore.read().orEmpty()

        _isRunning.value = true
        _isPaused.value = false
        overlayManager.isPaused = false
        _currentStatus.value = "Starting phone task..."

        _messages.value = _messages.value + PhoneChatMessage(
            sender = "user",
            text = instruction
        )

        agentJob = scope.launch {
            val maxSteps = 25
            val history = mutableListOf<String>()

            try {
                overlayManager.show(1, maxSteps, "Starting task...")
                delay(300)

                val metrics = phoneController.getDisplayMetrics()
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels

                for (step in 1..maxSteps) {
                    if (!_isRunning.value) break

                    while (_isPaused.value) {
                        overlayManager.update(step, maxSteps, "Paused")
                        delay(500)
                    }

                    overlayManager.update(step, maxSteps, "Inspecting active screen...")
                    _currentStatus.value = "Inspecting active screen..."

                    // 1. Capture screen UI hierarchy
                    val hierarchy = phoneController.dumpScreenHierarchy()
                    val screenSummary = hierarchy.toPromptString(maxElements = 40)

                    // 2. Build system prompt & prompt
                    val systemPrompt = """
You are an expert Autonomous Android Phone Controller Agent.
Your mission is to accomplish the user's task on an Android mobile device using accessibility gestures, element targeting, and app navigation.

Device Screen: ${screenWidth}x${screenHeight} pixels.

Available Actions (Respond with JSON or XML tool call):
1. {"action": "tap", "x": 540, "y": 960, "reason": "Tap element"}
2. {"action": "tap", "target": "[element_index_or_text]", "reason": "Click specific element"}
3. {"action": "long_press", "x": 540, "y": 960, "duration_ms": 1000, "reason": "Long press"}
4. {"action": "swipe", "startX": 540, "startY": 1500, "endX": 540, "endY": 400, "reason": "Swipe up next feed"}
5. {"action": "scroll_down", "reason": "Scroll down"}
6. {"action": "scroll_up", "reason": "Scroll up"}
7. {"action": "type", "text": "Search query", "reason": "Type text into focused field"}
8. {"action": "launch_app", "package": "com.android.settings", "reason": "Launch Settings app"}
9. {"action": "key_home", "reason": "Press Home"}
10. {"action": "key_back", "reason": "Press Back"}
11. {"action": "key_recents", "reason": "Open recent apps"}
12. {"action": "open_notifications", "reason": "Open notifications"}
13. {"action": "wait", "seconds": 2, "reason": "Wait for loading"}
14. {"action": "finish", "text": "Summary of what was completed"}

Rules:
- Choose coordinates or element indexes based on the visible screen elements list below.
- Keep reasoning clear and concise.
- When the goal is completed, output action "finish".
- Output valid JSON or XML <tool name="..."> format.
""".trimIndent()

                    val prompt = """
Current User Goal: "$instruction"
Step: $step / $maxSteps

Previous Actions:
${if (history.isEmpty()) "None (Starting now)" else history.takeLast(5).joinToString("\n")}

Current Screen State:
$screenSummary

Determine the single next action to take.
""".trimIndent()

                    val activeModelLabel = model.ifBlank { if (provider.isNotBlank()) provider else "NEBians Default" }
                    overlayManager.update(step, maxSteps, "Reasoning with $activeModelLabel...")
                    _currentStatus.value = "Reasoning with $activeModelLabel..."

                    val response = callLlm(
                        token = token,
                        provider = provider,
                        model = model,
                        providerId = providerId,
                        system = systemPrompt,
                        prompt = prompt
                    )

                    if (response == null) {
                        // Error was handled and message emitted
                        break
                    }

                    // Parse action and extract thinking
                    val parsedAction = AgentLlmToolParser.parseAction(response, defaultAction = "wait")
                    val thought = parsedAction.thought.ifBlank { "Executing step $step" }

                    overlayManager.update(step, maxSteps, thought)
                    _currentStatus.value = thought

                    // Execute action on device
                    val execOutcome = executeAction(parsedAction)
                    history.add("Step $step: [${parsedAction.actionName.uppercase()}] ${parsedAction.thought} -> $execOutcome")

                    _messages.value = _messages.value + PhoneChatMessage(
                        sender = "action",
                        text = execOutcome,
                        thought = parsedAction.thought,
                        actionType = parsedAction.actionName,
                        actionDetails = parsedAction.target.ifBlank { parsedAction.text }
                    )

                    if (parsedAction.actionName == "finish") {
                        val completionSummary = parsedAction.text.ifBlank { parsedAction.thought }.ifBlank { "Task successfully completed!" }
                        overlayManager.update(step, maxSteps, "Completed!")
                        _messages.value = _messages.value + PhoneChatMessage(
                            sender = "agent",
                            text = "✅ $completionSummary",
                            thought = parsedAction.thought
                        )
                        delay(1200)
                        break
                    }

                    delay(800)
                }
            } catch (e: CancellationException) {
                _currentStatus.value = "Stopped"
            } catch (e: Throwable) {
                _messages.value = _messages.value + PhoneChatMessage(
                    sender = "system",
                    text = "⚠️ Task error: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                _isRunning.value = false
                _currentStatus.value = "Ready"
                overlayManager.hide()
            }
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
                    _messages.value = _messages.value + PhoneChatMessage(
                        sender = "system",
                        text = "⚠️ Device is not connected to NEBians. Please open the Agent tab to connect Zeus, or select a configured AI provider in Settings."
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
                    _messages.value = _messages.value + PhoneChatMessage(
                        sender = "system",
                        text = "⚠️ LLM Error: $errMsg. Please check your provider configuration or switch models."
                    )
                    return@withContext null
                }

                if (res.reply.isBlank()) {
                    _messages.value = _messages.value + PhoneChatMessage(
                        sender = "system",
                        text = "⚠️ Model returned an empty response. Please retry."
                    )
                    return@withContext null
                }

                res.reply
            } catch (e: BackgroundAgentApiException) {
                _messages.value = _messages.value + PhoneChatMessage(
                    sender = "system",
                    text = "⚠️ API Error (${e.statusCode}): ${e.message}"
                )
                null
            } catch (e: Exception) {
                _messages.value = _messages.value + PhoneChatMessage(
                    sender = "system",
                    text = "⚠️ Network error contacting LLM: ${e.message ?: e.javaClass.simpleName}"
                )
                null
            }
        }
    }

    private suspend fun executeAction(action: ParsedLlmAction): String {
        return withContext(Dispatchers.Main) {
            when (action.actionName) {
                "tap" -> {
                    if (action.x != null && action.y != null) {
                        val ok = phoneController.tapCoordinates(action.x, action.y)
                        "Tapped at (${action.x.toInt()}, ${action.y.toInt()}) [Success: $ok]"
                    } else if (action.target.isNotBlank()) {
                        val idx = action.target.toIntOrNull()
                        if (idx != null) {
                            val ok = phoneController.clickElementByIndex(idx)
                            "Tapped element [$idx] [Success: $ok]"
                        } else {
                            val ok = phoneController.clickElementByText(action.target)
                            "Tapped text \"${action.target}\" [Success: $ok]"
                        }
                    } else {
                        val metrics = phoneController.getDisplayMetrics()
                        val cx = metrics.widthPixels * 0.5f
                        val cy = metrics.heightPixels * 0.5f
                        val ok = phoneController.tapCoordinates(cx, cy)
                        "Tapped screen center [Success: $ok]"
                    }
                }
                "long_press" -> {
                    val x = action.x ?: (phoneController.getDisplayMetrics().widthPixels * 0.5f)
                    val y = action.y ?: (phoneController.getDisplayMetrics().heightPixels * 0.5f)
                    val dur = action.durationMs ?: 1000L
                    val ok = phoneController.longPress(x, y, dur)
                    "Long pressed at (${x.toInt()}, ${y.toInt()}) for ${dur}ms [Success: $ok]"
                }
                "swipe" -> {
                    val metrics = phoneController.getDisplayMetrics()
                    val sx = action.startX ?: (metrics.widthPixels * 0.5f)
                    val sy = action.startY ?: (metrics.heightPixels * 0.75f)
                    val ex = action.endX ?: (metrics.widthPixels * 0.5f)
                    val ey = action.endY ?: (metrics.heightPixels * 0.25f)
                    val dur = action.durationMs ?: 350L
                    val ok = phoneController.swipe(sx, sy, ex, ey, dur)
                    "Swiped from (${sx.toInt()}, ${sy.toInt()}) to (${ex.toInt()}, ${ey.toInt()}) [Success: $ok]"
                }
                "scroll_down" -> {
                    val ok = phoneController.scrollDown()
                    "Scrolled down [Success: $ok]"
                }
                "scroll_up" -> {
                    val ok = phoneController.scrollUp()
                    "Scrolled up [Success: $ok]"
                }
                "scroll_left" -> {
                    val ok = phoneController.scrollLeft()
                    "Scrolled left [Success: $ok]"
                }
                "scroll_right" -> {
                    val ok = phoneController.scrollRight()
                    "Scrolled right [Success: $ok]"
                }
                "type" -> {
                    val textToType = action.text.ifBlank { action.target }
                    val ok = phoneController.inputText(textToType)
                    "Typed \"$textToType\" [Success: $ok]"
                }
                "launch_app" -> {
                    val app = action.packageName.ifBlank { action.target }.ifBlank { action.text }
                    val ok = phoneController.launchApp(app)
                    "Launched app: $app [Success: $ok]"
                }
                "key_home" -> {
                    val ok = phoneController.pressHome()
                    "Pressed Home key [Success: $ok]"
                }
                "key_back" -> {
                    val ok = phoneController.pressBack()
                    "Pressed Back key [Success: $ok]"
                }
                "key_recents" -> {
                    val ok = phoneController.pressRecents()
                    "Opened Recent Apps [Success: $ok]"
                }
                "open_notifications" -> {
                    val ok = phoneController.openNotifications()
                    "Opened Notifications [Success: $ok]"
                }
                "open_quick_settings" -> {
                    val ok = phoneController.openQuickSettings()
                    "Opened Quick Settings [Success: $ok]"
                }
                "take_screenshot" -> {
                    val bmp = phoneController.takeScreenshot()
                    "Captured screenshot [Success: ${bmp != null}]"
                }
                "wait" -> {
                    val waitMs = action.durationMs ?: 2000L
                    delay(waitMs)
                    "Waited ${waitMs / 1000}s"
                }
                "finish" -> {
                    "Completed task: ${action.text}"
                }
                else -> {
                    "Unknown action: ${action.actionName}"
                }
            }
        }
    }

    fun stop() {
        agentJob?.cancel()
        _isRunning.value = false
        _isPaused.value = false
        overlayManager.hide()
        _currentStatus.value = "Stopped"
        _messages.value = _messages.value + PhoneChatMessage(
            sender = "system",
            text = "⏹️ Task stopped by user."
        )
    }
}
