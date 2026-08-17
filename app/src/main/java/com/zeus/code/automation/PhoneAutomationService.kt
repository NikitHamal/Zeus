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
import android.os.SystemClock
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

        private val IME_SUBMIT_LABELS = listOf("search", "go", "send", "done", "submit", "ok", "enter")
        private const val RETRY_TIMEOUT_MS = 3500L
        private const val RETRY_INTERVAL_MS = 250L
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
     * Helper to poll until a condition succeeds or timeout elapses (OpenDroid / GenericAppAutomator style)
     */
    suspend fun retryUntilTimeout(timeoutMs: Long = RETRY_TIMEOUT_MS, attempt: suspend () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (attempt()) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            delay(RETRY_INTERVAL_MS)
        }
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

        // Ignore nodes completely outside screen
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
     * High-reliability Element Clicker with active polling (OpenDroid / GenericAppAutomator style).
     * Tries direct Accessibility Action on node or nearest clickable parent,
     * with fallback to center-coordinate gesture.
     */
    suspend fun clickElement(
        index: Int? = null,
        targetText: String? = null,
        resourceId: String? = null
    ): Boolean = withContext(Dispatchers.Main) {
        retryUntilTimeout(timeoutMs = 3000L) {
            val root = rootInActiveWindow ?: return@retryUntilTimeout false
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
                // 1. Direct Node Action
                val node = findNodeMatching(root, targetElement)
                if (node != null) {
                    val clicked = performClickOnNodeOrParent(node)
                    if (clicked) return@retryUntilTimeout true
                }

                // 2. Physical Touch gesture at center coordinates
                val cx = targetElement.centerX.toFloat()
                val cy = targetElement.centerY.toFloat()
                return@retryUntilTimeout clickCoordinate(cx, cy)
            }

            // Fallback: search whole accessibility tree for text
            if (!targetText.isNullOrBlank()) {
                val matchingNodes = root.findAccessibilityNodeInfosByText(targetText)
                if (!matchingNodes.isNullOrEmpty()) {
                    for (match in matchingNodes) {
                        if (match.isVisibleToUser) {
                            val rect = Rect()
                            match.getBoundsInScreen(rect)
                            if (rect.width() > 0 && rect.height() > 0) {
                                val clicked = performClickOnNodeOrParent(match)
                                if (clicked) return@retryUntilTimeout true
                                return@retryUntilTimeout clickCoordinate(rect.centerX().toFloat(), rect.centerY().toFloat())
                            }
                        }
                    }
                }
            }

            // Fallback: search view ID
            if (!resourceId.isNullOrBlank()) {
                val idNodes = root.findAccessibilityNodeInfosByViewId(resourceId)
                if (!idNodes.isNullOrEmpty()) {
                    for (match in idNodes) {
                        if (match.isVisibleToUser) {
                            val clicked = performClickOnNodeOrParent(match)
                            if (clicked) return@retryUntilTimeout true
                        }
                    }
                }
            }

            false
        }
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
     * Performs scroll on active scrollable container with fallback to touch swipe.
     */
    suspend fun performScroll(forward: Boolean): Boolean = withContext(Dispatchers.Main) {
        val root = rootInActiveWindow
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (root != null) {
            val scrolled = performScrollOnNode(root, action)
            if (scrolled) return@withContext true
        }

        // Fallback: Touch gesture scroll
        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels * 0.5f
        return@withContext if (forward) {
            swipe(cx, metrics.heightPixels * 0.72f, cx, metrics.heightPixels * 0.28f, 300L)
        } else {
            swipe(cx, metrics.heightPixels * 0.28f, cx, metrics.heightPixels * 0.72f, 300L)
        }
    }

    private fun performScrollOnNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (performScrollOnNode(child, action)) {
                return true
            }
        }
        return false
    }

    /**
     * High-reliability Text Input with auto-focusing, IME Enter action, and submit fallback (OpenDroid style).
     */
    suspend fun inputText(
        targetIndex: Int? = null,
        targetText: String? = null,
        textToType: String = "",
        clearFirst: Boolean = false,
        submit: Boolean = true
    ): Boolean = withContext(Dispatchers.Main) {
        // 1. If target element specified, click it first to activate the field and bring up the keyboard
        if (targetIndex != null || !targetText.isNullOrBlank()) {
            clickElement(index = targetIndex, targetText = targetText)
            delay(350)
        }

        // 2. Poll until focused or editable node is ready
        var success = retryUntilTimeout(timeoutMs = 3000L) {
            var root = rootInActiveWindow ?: return@retryUntilTimeout false

            var focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                ?: findFirstEditableNode(root)

            if (focused == null) {
                val nodes = dumpVisibleNodes()
                val candidate = nodes.firstOrNull {
                    it.isEditable || it.className.contains("EditText", ignoreCase = true) ||
                        it.text.contains("Search", ignoreCase = true) || it.contentDescription.contains("Search", ignoreCase = true) ||
                        it.resourceId.contains("search", ignoreCase = true) || it.resourceId.contains("query", ignoreCase = true)
                }
                if (candidate != null) {
                    clickCoordinate(candidate.centerX.toFloat(), candidate.centerY.toFloat())
                    delay(300)
                    root = rootInActiveWindow ?: return@retryUntilTimeout false
                    focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditableNode(root)
                }
            }

            if (focused != null) {
                focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

                if (clearFirst) {
                    val clearArgs = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                    }
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                    delay(80)
                }

                val currentText = if (clearFirst) "" else focused.text?.toString().orEmpty()
                val textToApply = if (currentText.isNotBlank()) "$currentText$textToType" else textToType

                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToApply)
                }
                var applied = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

                // Clipboard fallback
                if (!applied) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("zeus_input", textToApply))
                        applied = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                }

                return@retryUntilTimeout applied
            }

            false
        }

        // 3. If submit requested, trigger IME Enter / Search action (OpenDroid style)
        if (success && submit) {
            delay(250)
            performImeEnter()
        }

        success
    }

    /**
     * Performs the IME 'enter/search/go' action on the currently focused editable field (OpenDroid style).
     * Uses AccessibilityNodeInfo.ACTION_IME_ENTER on API 30+; on older APIs or fallback, finds submit controls.
     */
    fun performImeEnter(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        var submitted = false

        if (focused != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    submitted = focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
            } catch (_: Exception) {
            }
        }

        if (!submitted) {
            submitted = findAndClickSubmitControl(root, IME_SUBMIT_LABELS)
        }

        return submitted
    }

    private fun findAndClickSubmitControl(node: AccessibilityNodeInfo?, labels: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase(Locale.ROOT)
        val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT)
        val id = node.viewIdResourceName?.lowercase(Locale.ROOT)

        val matches = (text != null && labels.any { text.contains(it) }) ||
            (desc != null && labels.any { desc.contains(it) }) ||
            (id != null && (id.contains("search") || id.contains("submit") || id.contains("send") || id.contains("go")))

        if (matches && (node.isClickable || node.parent?.isClickable == true)) {
            val clicked = performClickOnNodeOrParent(node)
            if (clicked) return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickSubmitControl(child, labels)) {
                return true
            }
        }
        return false
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
