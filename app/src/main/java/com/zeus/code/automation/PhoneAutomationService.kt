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
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

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
            resourceId.isNotBlank() -> resourceId.substringAfterLast(":id/").substringAfterLast('/')
            else -> className.substringAfterLast('.')
        }

    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    fun toPromptSummary(): String {
        val label = displayLabel.replace("\n", " ").replace("\"", "'").trim().take(60)
        val tag = className.substringAfterLast('.')
        val idPart = if (resourceId.isNotBlank()) " id=\"${resourceId.substringAfterLast('/')}\"" else ""
        val clickableTag = if (isClickable) " [clickable]" else ""
        val editableTag = if (isEditable) " [editable/input]" else ""
        val scrollTag = if (isScrollable) " [scrollable]" else ""
        val focusedTag = if (isFocused) " [focused]" else ""
        return "[$index] <$tag$idPart$clickableTag$editableTag$scrollTag$focusedTag> \"$label\" center=($centerX,$centerY)"
    }
}

data class ScreenHierarchyDump(
    val packageName: String,
    val activityName: String,
    val nodes: List<UiElementNode>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPromptString(maxElements: Int = 50): String {
        val header = "Foreground App: $packageName\nScreen Activity: $activityName\nVisible Interactive Elements (${nodes.size}):"
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
            val screenMetrics = resources.displayMetrics
            val screenRect = Rect(0, 0, screenMetrics.widthPixels, screenMetrics.heightPixels)
            traverseNode(root, nodes, 0, parentClickable = false, parentBounds = null, screenRect = screenRect)
        }
        return ScreenHierarchyDump(
            packageName = currentPackageName.ifBlank { root?.packageName?.toString().orEmpty() }.ifBlank { "android" },
            activityName = currentActivityName.ifBlank { "MainActivity" },
            nodes = nodes
        )
    }

    fun dumpVisibleNodes(): List<UiElementNode> {
        return dumpScreenHierarchy().nodes
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        out: MutableList<UiElementNode>,
        depth: Int,
        parentClickable: Boolean,
        parentBounds: Rect?,
        screenRect: Rect
    ) {
        if (node == null) return
        if (!node.isVisibleToUser) {
            for (i in 0 until node.childCount) {
                traverseNode(node.getChild(i), out, depth + 1, parentClickable, parentBounds, screenRect)
            }
            return
        }

        val rect = Rect()
        node.getBoundsInScreen(rect)

        // Ignore nodes completely outside the screen
        if (rect.right <= 0 || rect.bottom <= 0 || rect.left >= screenRect.right || rect.top >= screenRect.bottom) {
            for (i in 0 until node.childCount) {
                traverseNode(node.getChild(i), out, depth + 1, parentClickable, parentBounds, screenRect)
            }
            return
        }

        // Effective clickability propagates from clickable ancestors
        val isClickable = node.isClickable || parentClickable
        val effectiveBounds = if (rect.width() > 0 && rect.height() > 0) rect else (parentBounds ?: rect)

        if (effectiveBounds.width() > 0 && effectiveBounds.height() > 0) {
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
                isClickable ||
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
                    bounds = effectiveBounds,
                    isClickable = isClickable,
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

        val currentClickable = node.isClickable || parentClickable
        val currentBounds = if (rect.width() > 0 && rect.height() > 0) rect else parentBounds

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), out, depth + 1, currentClickable, currentBounds, screenRect)
        }
    }

    // ==================== INTERACTION: ACTIONS + GESTURES ====================

    /**
     * Taps at coordinate (x, y) asynchronously.
     */
    suspend fun clickCoordinate(x: Float, y: Float): Boolean = withTimeoutOrNull(3000L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
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
     * Double taps at coordinate (x, y).
     */
    suspend fun doubleTapCoordinate(x: Float, y: Float): Boolean = withTimeoutOrNull(3000L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            }
            val stroke1 = GestureDescription.StrokeDescription(path, 0, 40)
            val stroke2 = GestureDescription.StrokeDescription(path, 100, 40)
            val gesture = GestureDescription.Builder().addStroke(stroke1).addStroke(stroke2).build()

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
     * Long presses at coordinate (x, y).
     */
    suspend fun longPressCoordinate(x: Float, y: Float, durationMs: Long = 1000L): Boolean = withTimeoutOrNull(durationMs + 2000L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(400L))
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
     * Dispatches a swipe gesture from (startX, startY) to (endX, endY).
     */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean = withTimeoutOrNull(durationMs + 2500L) {
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(startX.coerceAtLeast(0f), startY.coerceAtLeast(0f))
                lineTo(endX.coerceAtLeast(0f), endY.coerceAtLeast(0f))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(80L))
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
     * Robust Element Clicker: tries direct Accessibility Action first,
     * then falls back to clicking parent, then physical touch gesture on element center.
     */
    suspend fun clickElement(
        index: Int? = null,
        targetText: String? = null,
        resourceId: String? = null
    ): Boolean = withContext(Dispatchers.Main) {
        val root = rootInActiveWindow ?: return@withContext false
        val nodes = dumpVisibleNodes()

        val targetElement = when {
            index != null && index >= 0 && index < nodes.size -> nodes[index]
            !targetText.isNullOrBlank() -> {
                val lower = targetText.lowercase(Locale.ROOT).trim()
                nodes.firstOrNull { it.text.equals(targetText, ignoreCase = true) || it.contentDescription.equals(targetText, ignoreCase = true) }
                    ?: nodes.firstOrNull { it.text.lowercase(Locale.ROOT).contains(lower) || it.contentDescription.lowercase(Locale.ROOT).contains(lower) }
            }
            !resourceId.isNullOrBlank() -> {
                nodes.firstOrNull { it.resourceId.contains(resourceId, ignoreCase = true) }
            }
            else -> null
        }

        if (targetElement != null) {
            // Find corresponding node
            val node = findNodeMatching(root, targetElement)
            if (node != null) {
                val clickedDirectly = performClickOnNodeOrParent(node)
                if (clickedDirectly) return@withContext true
            }

            // Fallback: Physical Touch gesture at center coordinates
            val cx = targetElement.centerX.toFloat()
            val cy = targetElement.centerY.toFloat()
            return@withContext clickCoordinate(cx, cy)
        }

        false
    }

    private fun findNodeMatching(root: AccessibilityNodeInfo, element: UiElementNode): AccessibilityNodeInfo? {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (rect == element.bounds && (root.text?.toString().orEmpty() == element.text || root.viewIdResourceName.orEmpty() == element.resourceId)) {
            return root
        }
        for (i in 0 until root.childCount) {
            val found = findNodeMatching(root.getChild(i), element)
            if (found != null) return found
        }
        return null
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) return true
            }
            current = current.parent
        }
        return false
    }

    /**
     * Sets text into target element or currently focused field.
     * Automatically clicks and focuses the field if not already active!
     */
    suspend fun inputText(
        targetIndex: Int? = null,
        targetText: String? = null,
        textToType: String = "",
        clearFirst: Boolean = false,
        submit: Boolean = true
    ): Boolean = withContext(Dispatchers.Main) {
        val root = rootInActiveWindow ?: return@withContext false

        // 1. If target element specified, click it first to open keyboard / input focus
        if (targetIndex != null || !targetText.isNullOrBlank()) {
            clickElement(index = targetIndex, targetText = targetText)
            delay(350)
        }

        // 2. Find focused or editable node
        var focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            ?: findFirstEditableNode(root)

        if (focused == null) {
            // Try tapping top search area if no edit focus
            val nodes = dumpVisibleNodes()
            val searchNode = nodes.firstOrNull {
                it.isEditable || it.className.contains("EditText", ignoreCase = true) ||
                    it.text.contains("search", ignoreCase = true) || it.contentDescription.contains("search", ignoreCase = true)
            }
            if (searchNode != null) {
                clickCoordinate(searchNode.centerX.toFloat(), searchNode.centerY.toFloat())
                delay(400)
                val refreshedRoot = rootInActiveWindow
                focused = refreshedRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: findFirstEditableNode(refreshedRoot)
            }
        }

        if (focused != null) {
            focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val finalText = if (clearFirst) textToType else {
                val current = focused.text?.toString().orEmpty()
                if (current.isNotBlank()) "$current$textToType" else textToType
            }

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalText)
            }
            var success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            if (!success) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("zeus_input", finalText))
                    success = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
            }

            if (submit) {
                delay(200)
                // Trigger submit by clicking search action or Enter
                focused.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
            }

            return@withContext success
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

    fun performGlobalKey(actionId: Int): Boolean {
        return performGlobalAction(actionId)
    }

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
