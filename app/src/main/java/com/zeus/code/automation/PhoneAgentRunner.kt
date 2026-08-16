package com.zeus.code.automation

import android.content.Context
import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PhoneChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user", "agent", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionType: String? = null
)

class PhoneAgentRunner(
    private val context: Context,
    val phoneController: PhoneController,
    val overlayManager: PhoneOverlayManager = PhoneOverlayManager(context),
    private val api: BackgroundAgentApi = BackgroundAgentApi(context),
    private val tokenStore: SecureTokenStore = SecureTokenStore(context)
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var agentJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _messages = MutableStateFlow<List<PhoneChatMessage>>(
        listOf(
            PhoneChatMessage(
                sender = "agent",
                text = "Hello! I am your Autonomous Phone Controller Agent. Tell me what to do on your device (e.g. 'Open Settings and check storage', 'Open TikTok and auto-scroll 5 videos', 'Open YouTube and search for Lo-Fi'). I will show a live floating overlay and execute gestures in real-time."
            )
        )
    )
    val messages: StateFlow<List<PhoneChatMessage>> = _messages.asStateFlow()

    init {
        overlayManager.onPauseClicked = {
            val paused = !_isPaused.value
            _isPaused.value = paused
            overlayManager.isPaused = paused
        }
        overlayManager.onStopClicked = {
            stop()
        }
    }

    fun startTask(
        instruction: String,
        provider: String = "motiftech",
        model: String = "motif-102b",
        providerId: String = ""
    ) {
        if (_isRunning.value) return

        if (!PhoneAutomationService.isConnected) {
            _messages.value = _messages.value + PhoneChatMessage(
                sender = "system",
                text = "⚠️ Accessibility Service is not enabled. Please enable 'Zeus Automation' in Settings to allow touch/swipe gestures."
            )
            phoneController.openAccessibilitySettings()
            return
        }

        if (!overlayManager.hasOverlayPermission()) {
            _messages.value = _messages.value + PhoneChatMessage(
                sender = "system",
                text = "⚠️ Display over other apps permission is required for the live floating status overlay."
            )
            overlayManager.requestOverlayPermission()
            return
        }

        val token = tokenStore.get()
        if (token.isNullOrBlank()) {
            _messages.value = _messages.value + PhoneChatMessage(
                sender = "system",
                text = "⚠️ Please connect Zeus to NEBians from the Agent tab first to authenticate cloud LLM execution."
            )
            return
        }

        _isRunning.value = true
        _isPaused.value = false
        overlayManager.isPaused = false

        _messages.value = _messages.value + PhoneChatMessage(
            sender = "user",
            text = instruction
        )

        agentJob = scope.launch {
            val maxSteps = 15
            try {
                overlayManager.show(1, maxSteps, "Starting task...")
                delay(300)

                // Optional: navigate home or prepare
                overlayManager.update(1, maxSteps, "Inspecting current screen...")
                
                val history = mutableListOf<String>()

                for (step in 1..maxSteps) {
                    if (!_isRunning.value) break

                    while (_isPaused.value) {
                        overlayManager.update(step, maxSteps, "Paused")
                        delay(500)
                    }

                    overlayManager.update(step, maxSteps, "Analyzing screen nodes...")
                    val nodes = phoneController.dumpScreenNodes()

                    val nodesSummary = nodes.take(40).mapIndexed { idx, n ->
                        "[$idx] \"${n.text.ifBlank { n.contentDescription }}\" class=${n.className.substringAfterLast('.')} bounds=(${n.bounds.left},${n.bounds.top},${n.bounds.right},${n.bounds.bottom}) clickable=${n.isClickable}"
                    }.joinToString("\n")

                    val systemPrompt = """
You are an expert Autonomous Android Phone Controller Agent.
Your goal is to accomplish the user's task on a real Android device using accessibility gestures and UI hierarchy.

Available actions (MUST return strictly valid JSON):
1. {"action": "tap", "x": 540, "y": 960, "reason": "Click search button"}
2. {"action": "swipe", "startX": 500, "startY": 1600, "endX": 500, "endY": 400, "reason": "Swipe up next feed"}
3. {"action": "launch_app", "package": "com.android.settings", "reason": "Open Settings app"}
4. {"action": "key_home", "reason": "Press Home"}
5. {"action": "key_back", "reason": "Press Back"}
6. {"action": "wait", "seconds": 3, "reason": "Wait for loading"}
7. {"action": "finish", "message": "Completed task successfully!"}

Current User Goal: $instruction
Past actions: ${history.takeLast(4).joinToString("; ")}
Current visible screen elements:
$nodesSummary

Respond ONLY with a JSON object describing the single next action.
""".trimIndent()

                    val response = api.chat(
                        token = token,
                        provider = provider,
                        model = model,
                        prompt = "Determine next action for step $step to achieve: $instruction",
                        providerId = providerId,
                        system = systemPrompt
                    )

                    if (!response.ok) {
                        _messages.value = _messages.value + PhoneChatMessage(
                            sender = "agent",
                            text = "❌ LLM Error: ${response.error ?: "Failed to generate action"}"
                        )
                        break
                    }

                    val replyText = response.reply.trim()
                    val cleanJson = replyText.substringAfter("```json").substringAfter("```").substringBefore("```").trim()
                    val jsonStart = cleanJson.indexOf('{')
                    val jsonEnd = cleanJson.lastIndexOf('}')
                    
                    if (jsonStart == -1 || jsonEnd == -1) {
                        overlayManager.update(step, maxSteps, "Thinking...")
                        history.add("LLM gave non-JSON: $replyText")
                        delay(1000)
                        continue
                    }

                    val jsonStr = cleanJson.substring(jsonStart, jsonEnd + 1)
                    val actionObj = runCatching { json.parseToJsonElement(jsonStr).jsonObject }.getOrNull()

                    if (actionObj == null) {
                        history.add("Failed to parse action JSON: $jsonStr")
                        continue
                    }

                    val actionType = actionObj["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "wait"
                    val reason = actionObj["reason"]?.jsonPrimitive?.contentOrNull ?: actionType

                    overlayManager.update(step, maxSteps, "Executing $reason...")
                    history.add("Step $step: $reason")

                    _messages.value = _messages.value + PhoneChatMessage(
                        sender = "agent",
                        text = "Step $step: $reason",
                        actionType = actionType
                    )

                    when (actionType) {
                        "tap" -> {
                            val x = actionObj["x"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 540f
                            val y = actionObj["y"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 960f
                            phoneController.tapCoordinates(x, y)
                            delay(1200)
                        }
                        "swipe" -> {
                            val startX = actionObj["startX"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 540f
                            val startY = actionObj["startY"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 1500f
                            val endX = actionObj["endX"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 540f
                            val endY = actionObj["endY"]?.jsonPrimitive?.intOrNull?.toFloat() ?: 400f
                            phoneController.swipe(startX, startY, endX, endY)
                            delay(1500)
                        }
                        "launch_app" -> {
                            val pkg = actionObj["package"]?.jsonPrimitive?.contentOrNull ?: "com.android.settings"
                            phoneController.launchApp(pkg)
                            delay(2000)
                        }
                        "key_home" -> {
                            phoneController.pressHome()
                            delay(1000)
                        }
                        "key_back" -> {
                            phoneController.pressBack()
                            delay(1000)
                        }
                        "wait" -> {
                            val secs = actionObj["seconds"]?.jsonPrimitive?.intOrNull ?: 2
                            delay(secs * 1000L)
                        }
                        "finish" -> {
                            val msg = actionObj["message"]?.jsonPrimitive?.contentOrNull ?: "Goal accomplished."
                            overlayManager.update(step, maxSteps, "Completed!")
                            delay(1500)
                            _messages.value = _messages.value + PhoneChatMessage(
                                sender = "agent",
                                text = "✅ $msg"
                            )
                            break
                        }
                    }
                }
            } catch (e: Throwable) {
                _messages.value = _messages.value + PhoneChatMessage(
                    sender = "agent",
                    text = "⚠️ Task interrupted: ${e.message ?: e.javaClass.simpleName}"
                )
            } finally {
                _isRunning.value = false
                overlayManager.hide()
            }
        }
    }

    fun stop() {
        agentJob?.cancel()
        _isRunning.value = false
        _isPaused.value = false
        overlayManager.hide()
        _messages.value = _messages.value + PhoneChatMessage(
            sender = "system",
            text = "⏹️ Task stopped by user."
        )
    }
}
