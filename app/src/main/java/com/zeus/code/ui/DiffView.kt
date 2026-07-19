package com.zeus.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class DiffLineKind { FILE_HEADER, HUNK, ADDED, REMOVED, META, CONTEXT }

private data class DiffLine(val text: String, val kind: DiffLineKind)

/**
 * Renders a unified diff as a readable, color-coded, horizontally scrollable
 * block: file headers tinted, hunks marked, additions green, removals red.
 */
@Composable
fun DiffView(diff: String, modifier: Modifier = Modifier) {
    val lines = rememberDiffLines(diff)
    val addedBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    val removedBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val hunkColor = MaterialTheme.colorScheme.primary
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        lines.forEach { line ->
            val (bg, fg, weight) = when (line.kind) {
                DiffLineKind.FILE_HEADER -> Triple(headerBg, bodyColor, FontWeight.SemiBold)
                DiffLineKind.HUNK -> Triple(null, hunkColor, FontWeight.Medium)
                DiffLineKind.ADDED -> Triple(addedBg, bodyColor, FontWeight.Normal)
                DiffLineKind.REMOVED -> Triple(removedBg, bodyColor, FontWeight.Normal)
                DiffLineKind.META -> Triple(null, metaColor, FontWeight.Normal)
                DiffLineKind.CONTEXT -> Triple(null, bodyColor.copy(alpha = 0.8f), FontWeight.Normal)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (bg != null) Modifier.background(bg) else Modifier)
            ) {
                Text(
                    text = line.text.ifEmpty { " " },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    fontWeight = weight,
                    color = fg,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.5.dp)
                )
            }
        }
    }
}

private fun rememberDiffLines(diff: String): List<DiffLine> {
    val out = ArrayList<DiffLine>(minOf(diff.lineSequence().count(), 4000))
    var truncated = false
    for ((index, raw) in diff.lineSequence().withIndex()) {
        if (index >= 4000) { truncated = true; break }
        val kind = when {
            raw.startsWith("diff --git") -> DiffLineKind.FILE_HEADER
            raw.startsWith("new file") || raw.startsWith("deleted file") ||
                raw.startsWith("index ") || raw.startsWith("similarity index") ||
                raw.startsWith("rename from") || raw.startsWith("rename to") ||
                raw.startsWith("Binary files") -> DiffLineKind.META
            raw.startsWith("---") -> DiffLineKind.META
            raw.startsWith("+++") -> DiffLineKind.META
            raw.startsWith("@@") -> DiffLineKind.HUNK
            raw.startsWith("+") -> DiffLineKind.ADDED
            raw.startsWith("-") -> DiffLineKind.REMOVED
            else -> DiffLineKind.CONTEXT
        }
        // Show "a/path -> b/path" file headers compactly.
        val text = if (kind == DiffLineKind.FILE_HEADER) {
            raw.removePrefix("diff --git ").replace("b/", "").replace("a/", "")
        } else raw
        out += DiffLine(text, kind)
    }
    if (truncated) out += DiffLine("... diff truncated for display ...", DiffLineKind.META)
    return out
}
