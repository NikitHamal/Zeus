
package com.zeus.data.git.models

enum class DiffLineType {
    CONTEXT,
    ADDED,
    REMOVED,
    HEADER,
    HUNK_HEADER
}

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldLineNum: Int? = null,
    val newLineNum: Int? = null
)

data class DiffHunk(
    val header: String,
    val lines: List<DiffLine>
)

data class FileDiff(
    val filePath: String,
    val oldPath: String? = null,
    val changeType: ChangeType,
    val hunks: List<DiffHunk> = emptyList(),
    val isBinary: Boolean = false
) {
    val addedLines: Int get() = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.ADDED } }
    val removedLines: Int get() = hunks.sumOf { h -> h.lines.count { it.type == DiffLineType.REMOVED } }
}
