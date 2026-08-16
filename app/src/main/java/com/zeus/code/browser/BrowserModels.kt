package com.zeus.code.browser

import kotlinx.serialization.Serializable

@Serializable
data class DomElementInfo(
    val zeusId: String = "",
    val tagName: String = "",
    val text: String = "",
    val href: String = "",
    val placeholder: String = "",
    val ariaLabel: String = "",
    val inputType: String = "",
    val value: String = "",
    val selector: String = "",
    val isInteractive: Boolean = true,
    val isVisible: Boolean = true,
    val bounds: DomRect = DomRect()
) {
    val displayLabel: String
        get() = when {
            text.isNotBlank() -> text
            placeholder.isNotBlank() -> placeholder
            ariaLabel.isNotBlank() -> ariaLabel
            value.isNotBlank() -> value
            href.isNotBlank() -> href
            else -> "<$tagName>"
        }

    fun toPromptSummary(): String {
        val label = displayLabel.replace("\n", " ").trim().take(50)
        val typeTag = if (inputType.isNotBlank()) " type=\"$inputType\"" else ""
        val hrefTag = if (href.isNotBlank()) " href=\"${href.take(40)}\"" else ""
        val targetId = if (zeusId.isNotBlank()) " id=\"$zeusId\"" else " sel=\"$selector\""
        return "[$zeusId] <$tagName$typeTag$targetId$hrefTag> \"$label\""
    }
}

@Serializable
data class DomRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
)

@Serializable
data class PageLink(
    val text: String = "",
    val url: String = ""
)

@Serializable
data class BrowserPageContent(
    val url: String = "",
    val title: String = "",
    val textContent: String = "",
    val elements: List<DomElementInfo> = emptyList(),
    val links: List<PageLink> = emptyList()
) {
    fun toPromptSummary(maxElements: Int = 45): String {
        val header = "Page Title: $title\nURL: $url\n\nInteractive Elements (${elements.size}):"
        val elementsList = elements.take(maxElements).joinToString("\n") { it.toPromptSummary() }
        val footer = if (elements.size > maxElements) "\n... (${elements.size - maxElements} more interactive elements)" else ""
        val contentSnippet = if (textContent.isNotBlank()) {
            "\n\nPage Text Snippet:\n" + textContent.take(2500).trim()
        } else ""
        return "$header\n$elementsList$footer$contentSnippet"
    }
}

data class BrowserActionResult(
    val success: Boolean,
    val message: String,
    val data: Any? = null
)
