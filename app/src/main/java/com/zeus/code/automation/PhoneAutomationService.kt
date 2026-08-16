package com.zeus.code.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class UiElementNode(
    val index: Int = 0,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val resourceId: String = "",
    val bounds: Rect = Rect(),
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val isChecked: Boolean = false,
    val isEnabled: Boolean = true,
    val hintText: String = "",
    val depth: Int = 0
) {
    val displayLabel: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            hintText.isNotBlank() -> hintText
            resourceId.isNotBlank() -> resourceId.substringAfterLast(":id/")
            else -> className.substringAfterLast('.')
        }

    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    fun toPromptSummary(): String {
        val label = displayLabel.replace("\n", " ").trim()
        val tag = className.substringAfterLast('.')
        val idPart = if (resourceId.isNotBlank()) " id=\"${resourceId.substringAfterLast('/')}\"" else ""
        val clickableTag = if (isClickable) " [clickable]" else ""
        val editableTag = if (isEditable) " [editable]" else ""
        val scrollTag = if (isScrollable) " [scrollable]" else ""
        val focusedTag = if (isFocused) " [focused]" else ""
        return "[$index] <$tag$idPart$clickableTag$editableTag$scrollTag$focusedTag> \"$label\" bounds=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) center=($centerX,$centerY)"
    }
}

data class ScreenHierarchyDump(
    val packageName: String,
    val activityName: String,
    val nodes: List<UiElementNode>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPromptString(maxElements: Int = 45): String {
        val header = "Foreground App: $packageName ($activityName)\nTotal visible elements: ${nodes.size}"
        val elementsList = nodes.take(maxElements).joinToString("\n") { it.toPromptSummary() }
        val footer = if (nodes.size > maxElements) "\n... (${nodes.size - maxElements} more elements)" else ""
        return "$header\n$elementsList$footer"
    }
}

class PhoneAutomationService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PhoneAutomationService? = null
            private set

        val isConnected: Boolean
            get() = instance != null
    }

    var currentPackageName: String = ""
        private set

    var currentActivityName: String = ""
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            currentPackageName = pkg
        }

        val cls = event.className?.toString()
        if (!cls.isNullOrBlank() && (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED)) {
            currentActivityName = cls
        }
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Dumps all visible interactive and informational UI nodes currently in the active window.
     */
    fun dumpScreenHierarchy(): ScreenHierarchyDump {
        val root = rootInActiveWindow
        val nodes = mutableListOf<UiElementNode>()
        if (root != null) {
            traverseNode(root, nodes, 0)
        }
        return ScreenHierarchyDump(
            packageName = currentPackageName.ifBlank { root?.packageName?.toString().orEmpty() }.ifBlank { "android" },
            activityName = currentActivityName.ifBlank { "UnknownActivity" },
            nodes = nodes
        )
    }

    fun dumpVisibleNodes(): List<UiElementNode> {
        return dumpScreenHierarchy().nodes
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        out: MutableList<UiElementNode>,
        depth: Int
    ) {
        if (node == null) return
        if (!node.isVisibleToUser) {
            // Traverse children even if parent is container
            for (i in 0 until node.childCount) {
                traverseNode(node.getChild(i), out, depth + 1)
            }
            return
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)

        // Ignore empty / 0-sized nodes
        if (rect.width() > 0 && rect.height() > 0) {
            val text = node.text?.toString().orEmpty().trim()
            val desc = node.contentDescription?.toString().orEmpty().trim()
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                node.hintText?.toString().orEmpty().trim()
            } else ""
            val resId = node.viewIdResourceName.orEmpty().trim()
            val clazz = node.className?.toString().orEmpty()

            val isImportant = text.isNotBlank() ||
                desc.isNotBlank() ||
                hint.isNotBlank() ||
                node.isClickable ||
                node.isEditable ||
                node.isScrollable ||
                node.isCheckable ||
                node.isFocusable

            if (isImportant) {
                val element = UiElementNode(
                    index = out.size,
                    text = text,
                    contentDescription = desc,
                    className = clazz,
                    resourceId = resId,
                    bounds = rect,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isFocused = node.isFocused,
                    isSelected = node.isSelected,
                    isChecked = node.isChecked,
                    isEnabled = node.isEnabled,
                    hintText = hint,
                    depth = depth
                )
                out.add(element)
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), out, depth + 1)
        }
    }

    /**
     * Taps at coordinate (x, y) asynchronously and returns whether gesture completed successfully.
     */
    suspend fun clickCoordinate(x: Float, y: Float): Boolean = withTimeoutOrNull(3000L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    } ?: false

    /**
     * Long presses at coordinate (x, y) with a configurable duration.
     */
    suspend fun longPressCoordinate(x: Float, y: Float, durationMs: Long = 1000L): Boolean = withTimeoutOrNull(durationMs + 2000L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(500L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    } ?: false

    /**
     * Dispatches a swipe gesture from (startX, startY) to (endX, endY) over durationMs.
     */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L
    ): Boolean = withTimeoutOrNull(durationMs + 2500L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(startX.coerceAtLeast(0f), startY.coerceAtLeast(0f))
                lineTo(endX.coerceAtLeast(0f), endY.coerceAtLeast(0f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)

            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }
    } ?: false

    /**
     * Inputs text into the currently focused or editable element.
     * Tries ACTION_SET_TEXT first, then falls back to clipboard paste.
     */
    suspend fun inputText(text: String, clearFirst: Boolean = false): Boolean = withContext(Dispatchers.Main) {
        val root = rootInActiveWindow ?: return@withContext false

        // Try finding focused input
        var focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

        if (focused == null) {
            // Find first editable node
            focused = findFirstEditableNode(root)
        }

        if (focused != null) {
            val finalText = if (clearFirst) text else {
                val current = focused.text?.toString().orEmpty()
                if (current.isNotBlank()) "$current$text" else text
            }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
            }
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) return@withContext true

            // Fallback: clipboard paste
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("zeus_input", finalText))
                focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val pasted = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                if (pasted) return@withContext true
            }
        }
        false
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * Performs a global accessibility action (e.g. Back, Home, Recents, Notifications, Screenshot).
     */
    fun performGlobalKey(actionId: Int): Boolean {
        return performGlobalAction(actionId)
    }

    /**
     * Takes screenshot on Android 11+ (API 30+) using AccessibilityService.
     */
    suspend fun takeScreenshotBitmap(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine { continuation ->
                val executor = Executor { command -> command.run() }
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                                val copy = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                hardwareBuffer.close()
                                if (continuation.isActive) continuation.resume(copy)
                            } catch (_: Exception) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }
        }
    }
}
