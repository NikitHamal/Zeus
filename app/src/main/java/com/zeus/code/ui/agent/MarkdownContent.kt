package com.zeus.code.ui.agent

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Renders markdown (fenced code blocks, inline code, bold/italic, lists,
 * headings, links) in a native TextView via Markwon. Uses the platform font
 * so glyphs missing from Poppins (arrows, box chars, emoji) render fine.
 */
@Composable
internal fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color? = null,
    linkColor: Color? = null,
    textSize: TextUnit = 13.5.sp
) {
    val context = LocalContext.current
    val defaultColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val resolvedColor = (textColor ?: defaultColor).toArgb()
    val resolvedLink = (linkColor ?: accent).toArgb()
    val px = with(density) { textSize.toPx() }

    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    AndroidView(
        factory = {
            TextView(it).apply {
                setTextColor(resolvedColor)
                setLinkTextColor(resolvedLink)
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
                // Markdown is the source of truth for wrap behavior; never reuse
                // TextView width from a recycled view.
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        },
        modifier = modifier,
        update = { view ->
            view.setTextColor(resolvedColor)
            view.setLinkTextColor(resolvedLink)
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px)
            markwon.setMarkdown(view, markdown)
        }
    )
}
