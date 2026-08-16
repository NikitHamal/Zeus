package com.zeus.code.browser

import kotlinx.serialization.Serializable

@Serializable
data class DomElementInfo(
    val id: String = "",
    val tagName: String,
    val text: String = "",
    val href: String = "",
    val selector: String = "",
    val isInteractive: Boolean = false
)

@Serializable
data class BrowserPageContent(
    val url: String,
    val title: String,
    val textContent: String,
    val elements: List<DomElementInfo> = emptyList()
)

sealed class BrowserAgentAction {
    data class Navigate(val url: String) : BrowserAgentAction()
    data class Click(val selector: String) : BrowserAgentAction()
    data class TypeText(val selector: String, val text: String) : BrowserAgentAction()
    data class Scroll(val deltaY: Int) : BrowserAgentAction()
    data class EvaluateScript(val script: String) : BrowserAgentAction()
    object ExtractContent : BrowserAgentAction()
    object CaptureScreenshot : BrowserAgentAction()
}

data class BrowserActionResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)
