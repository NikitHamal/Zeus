package com.zeus.code.automation

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class PhoneTaskLog(
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PhoneController(private val context: Context) {

    private val _logs = MutableStateFlow<List<PhoneTaskLog>>(emptyList())
    val logs: StateFlow<List<PhoneTaskLog>> = _logs.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    fun checkServiceState() {
        _isServiceActive.value = PhoneAutomationService.isConnected
    }

    private fun addLog(action: String, details: String) {
        val entry = PhoneTaskLog(action = action, details = details)
        _logs.value = _logs.value + entry
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    suspend fun pressHome(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        addLog("HOME", if (success) "Navigated to Home screen" else "Failed to navigate Home")
        success
    }

    suspend fun pressBack(): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        addLog("BACK", if (success) "Triggered Back button" else "Failed to trigger Back")
        success
    }

    suspend fun tapCoordinates(x: Float, y: Float): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        var result = false
        service.clickCoordinate(x, y) { success ->
            result = success
            addLog("TAP", "Tapped at ($x, $y) - Success: $success")
        }
        result
    }

    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        var result = false
        service.swipe(startX, startY, endX, endY) { success ->
            result = success
            addLog("SWIPE", "Swiped from ($startX, $startY) to ($endX, $endY)")
        }
        result
    }

    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            addLog("LAUNCH_APP", "Launched package: $packageName")
            true
        } else {
            addLog("LAUNCH_APP", "App package not found: $packageName")
            false
        }
    }

    suspend fun inputText(text: String): Boolean = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext false
        val success = service.setFocusedText(text)
        addLog("INPUT", "Typed \"$text\" - Success: $success")
        success
    }

    suspend fun dumpScreenNodes(): List<UiElementNode> = withContext(Dispatchers.Main) {
        val service = PhoneAutomationService.instance ?: return@withContext emptyList()
        val nodes = service.dumpVisibleNodes()
        addLog("INSPECT_SCREEN", "Captured ${nodes.size} UI elements on screen")
        nodes
    }
}
