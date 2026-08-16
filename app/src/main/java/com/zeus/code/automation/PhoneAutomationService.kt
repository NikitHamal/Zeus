package com.zeus.code.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class UiElementNode(
    val text: String,
    val className: String,
    val resourceId: String,
    val contentDescription: String,
    val bounds: Rect,
    val isClickable: Boolean
)

class PhoneAutomationService : AccessibilityService() {

    companion object {
        var instance: PhoneAutomationService? = null
            private set

        val isConnected: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Listening for window & content state changes
    }

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun dumpVisibleNodes(): List<UiElementNode> {
        val root = rootInActiveWindow ?: return emptyList()
        val list = mutableListOf<UiElementNode>()
        traverseNode(root, list)
        return list
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, out: MutableList<UiElementNode>) {
        if (node == null) return
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val resId = node.viewIdResourceName.orEmpty()
        val clazz = node.className?.toString().orEmpty()

        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable) {
            out.add(
                UiElementNode(
                    text = text,
                    className = clazz,
                    resourceId = resId,
                    contentDescription = desc,
                    bounds = rect,
                    isClickable = node.isClickable
                )
            )
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), out)
        }
    }

    fun clickCoordinate(x: Float, y: Float, onComplete: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke(false)
            }
        }, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300, onComplete: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                onComplete?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                onComplete?.invoke(false)
            }
        }, null)
    }
}
