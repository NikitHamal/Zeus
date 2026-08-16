package com.zeus.code.automation

import android.content.Context
import com.zeus.code.data.AgentLlmToolParser
import com.zeus.code.data.AgentPlanItem
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

    // Configurable model settings with reactive StateFlows
    private val _thinkingMode = MutableStateFlow("auto")
    val thinkingMode: StateFlow<String> = _thinkingMode.asStateFlow()

    private val _webSearch = MutableStateFlow(false)
    val webSearch: StateFlow<Boolean> = _webSearch.asStateFlow()

    var temperature: Float = 0.2f
    var maxTokens: Int = 4096

    fun setThinkingMode(mode: String) {
        _thinkingMode.value = mode
    }

    fun setWebSearch(enabled: Boolean) {
        _webSearch.value = enabled
    }

    fun toggleWebSearch() {
        _webSearch.value = !_webSearch.value
    }

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
        provider: String = "qwen",
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
            val maxSafetyIterations = 35
            val history = mutableListOf<String>()
            var previousPackage = ""
            var previousActivity = ""
            var activePlan = listOf<AgentPlanItem>()

            try {
                overlayManager.show("Step 1", "Starting task...")
                delay(300)

                val metrics = phoneController.getDisplayMetrics()
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels

                for (step in 1..maxSafetyIterations) {
                    if (!_isRunning.value) break

                    val stepTitle = if (activePlan.isNotEmpty()) {
                        val done = activePlan.count { it.isCompleted }
                        val total = activePlan.size
                        "Plan $done/$total · Step $step"
                    } else {
                        "Step $step"
                    }

                    while (_isPaused.value) {
                        overlayManager.update(stepTitle, "Paused")
                        delay(500)
                    }

                    overlayManager.update(stepTitle, "Inspecting active screen...")
                    _currentStatus.value = "Inspecting active screen..."

                    // 1. Capture screen UI hierarchy
                    val hierarchy = phoneController.dumpScreenHierarchy()
                    val screenSummary = hierarchy.toPromptString(maxElements = 45)

                    val screenTransitionNote = if (previousPackage.isNotBlank() && previousPackage != hierarchy.packageName) {
                        "Screen transitioned: Now in app ${hierarchy.packageName} (${hierarchy.activityName})"
                    } else ""
                    previousPackage = hierarchy.packageName
                    previousActivity = hierarchy.activityName

                    // 2. Build system prompt with Cognitive Framework
                    val systemPrompt = """
You are an expert Autonomous Android Phone Controller Agent.
Your mission is to accomplish the user's task on an Android mobile device using accessibility gestures, element targeting, and app navigation.

Device Screen: ${screenWidth}x${screenHeight} pixels.

Cognitive Framework:
Before outputting your action, reason step-by-step in <think>...</think>:
1. Observation: What app & screen am I looking at? What are the key visible elements and fields?
2. Analysis: Did the previous step succeed? Is the goal or next sub-goal visible on screen?
3. Plan: What is the single best next action?

Available Actions (Respond with JSON or XML tool call):
1. {"action": "tap", "target": "[element_index_or_text]", "reason": "Click specific element"}
2. {"action": "tap", "x": 540, "y": 960, "reason": "Tap coordinates"}
3. {"action": "double_tap", "x": 540, "y": 960, "reason": "Double tap video or image"}
4. {"action": "long_press", "x": 540, "y": 960, "duration_ms": 1000, "reason": "Long press"}
5. {"action": "swipe", "startX": 540, "startY": 1500, "endX": 540, "endY": 400, "reason": "Swipe feed"}
6. {"action": "scroll_down", "reason": "Scroll down"}
7. {"action": "scroll_up", "reason": "Scroll up"}
8. {"action": "type", "text": "search query", "clear_first": false, "submit": true, "reason": "Type text into search bar"}
9. {"action": "launch_app", "package": "com.android.settings", "reason": "Launch app by name or package"}
10. {"action": "open_url", "url": "https://example.com", "reason": "Open web link"}
11. {"action": "key_home", "reason": "Press Home"}
12. {"action": "key_back", "reason": "Press Back"}
13. {"action": "key_recents", "reason": "Open recent apps"}
14. {"action": "open_notifications", "reason": "Open notifications"}
15. {"action": "wait", "seconds": 2, "reason": "Wait for loading"}
16. {"action": "take_over", "reason": "Ask human user to solve biometric / OTP / CAPTCHA"}
17. {"action": "finish", "text": "Summary of what was accomplished"}

Optional: You can include "plan": [{"content": "step description", "status": "pending|in_progress|completed"}] to update your dynamic plan.

Rules:
- Target elements using the [index] identifier or direct visible label from the screen elements list.
- When typing into search inputs, set "submit": true to automatically trigger search.
- When the goal is completed, output action "finish".
- Always output valid JSON or XML format.
""".trimIndent()

                    val planSection = if (activePlan.isNotEmpty()) {
                        "\nActive Plan:\n" + activePlan.joinToString("\n") {
                            "- [${if (it.isCompleted) "x" else if (it.isInProgress) ">" else " "}] ${it.title}"
                        } + "\n"
                    } else ""

                    val prompt = """
Current User Goal: "$instruction"
Step: $step
$planSection${if (screenTransitionNote.isNotBlank()) "\nState Notice: $screenTransitionNote\n" else ""}
Previous Actions:
${if (history.isEmpty()) "None (Starting now)" else history.takeLast(5).joinToString("\n")}

Current Screen State:
$screenSummary

Determine the single next action to take.
""".trimIndent()

                    val activeModelLabel = model.ifBlank { if (provider.isNotBlank()) provider else "Qwen 3.8 Max" }
                    overlayManager.update(stepTitle, "Reasoning with $activeModelLabel...")
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
                        break
                    }

                    // Parse action and extract thinking & plan
                    val parsedAction = AgentLlmToolParser.parseAction(response, defaultAction = "wait")
                    if (parsedAction.planItems.isNotEmpty()) {
                        activePlan = parsedAction.planItems
                    }

                    val thought = parsedAction.thought.ifBlank { "Executing step $step" }
                    overlayManager.update(stepTitle, thought)
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
                        overlayManager.update("Completed ✅", completionSummary)
                        _messages.value = _messages.value + PhoneChatMessage(
                            sender = "agent",
                            text = "✅ $completionSummary",
                            thought = parsedAction.thought
                        )
                        delay(1500)
                        break
                    }

                    if (parsedAction.actionName == "take_over") {
                        overlayManager.update("Takeover Needed ✋", "User action required")
                        _messages.value = _messages.value + PhoneChatMessage(
                            sender = "agent",
                            text = "✋ User takeover required: ${parsedAction.text.ifBlank { parsedAction.thought }}",
                            thought = parsedAction.thought
                        )
                        delay(1500)
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
                    prompt = prompt,
                    thinkingMode = _thinkingMode.value,
                    webSearch = _webSearch.value,
                    temperature = temperature,
                    maxTokens = maxTokens
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
                "tap", "click" -> {
                    if (action.target.isNotBlank()) {
                        val ok = phoneController.clickElement(action.target)
                        "Clicked element \"${action.target}\" [Success: $ok]"
                    } else if (action.x != null && action.y != null) {
                        val ok = phoneController.tapCoordinates(action.x, action.y)
                        "Tapped at (${action.x.toInt()}, ${action.y.toInt()}) [Success: $ok]"
                    } else {
                        val metrics = phoneController.getDisplayMetrics()
                        val cx = metrics.widthPixels * 0.5f
                        val cy = metrics.heightPixels * 0.5f
                        val ok = phoneController.tapCoordinates(cx, cy)
                        "Tapped screen center [Success: $ok]"
                    }
                }
                "double_tap" -> {
                    val x = action.x ?: (phoneController.getDisplayMetrics().widthPixels * 0.5f)
                    val y = action.y ?: (phoneController.getDisplayMetrics().heightPixels * 0.5f)
                    val ok = phoneController.doubleTapCoordinates(x, y)
                    "Double tapped at (${x.toInt()}, ${y.toInt()}) [Success: $ok]"
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
                    val dur = action.durationMs ?: 300L
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
                "type", "input" -> {
                    val textToType = action.text.ifBlank { action.rawParameters["query"] }.orEmpty()
                    val target = action.target.takeIf { it.isNotBlank() && it != textToType }
                    val clearFirst = action.rawParameters["clear_first"]?.equals("true", ignoreCase = true) ?: false
                    val submit = action.rawParameters["submit"]?.equals("true", ignoreCase = true) ?: true
                    val ok = phoneController.inputText(
                        text = textToType,
                        target = target,
                        clearFirst = clearFirst,
                        submit = submit
                    )
                    "Entered \"$textToType\" ${if (target != null) "into \"$target\"" else ""} [Success: $ok]"
                }
                "launch_app" -> {
                    val app = action.packageName.ifBlank { action.target }.ifBlank { action.text }
                    val ok = phoneController.launchApp(app)
                    "Launched app: $app [Success: $ok]"
                }
                "open_url" -> {
                    val url = action.target.ifBlank { action.text }
                    val ok = phoneController.openUrl(url)
                    "Opened URL: $url [Success: $ok]"
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
                "take_over" -> {
                    "Requested human takeover: ${action.text.ifBlank { action.thought }}"
                }
                "finish" -> {
                    "Completed task: ${action.text}"
                }
                else -> {
                    "Executed: ${action.actionName}"
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
