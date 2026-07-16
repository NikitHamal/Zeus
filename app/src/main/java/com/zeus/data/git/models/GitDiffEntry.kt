package com.zeus.data.git.models

/**
 * Detailed diff entry between working tree / index / commits.
 */
data class GitDiffEntry(
    val oldPath: String,
    val newPath: String,
    val changeType: DiffChangeType,
    val oldId: String? = null,
    val newId: String? = null,
    val isBinary: Boolean = false,
    val patch: String? = null, // unified diff text if requested
    val addedLines: Int = 0,
    val deletedLines: Int = 0
) {
    val effectivePath: String
        get() = if (changeType == DiffChangeType.DELETE) oldPath else newPath

    val fileName: String
        get() = effectivePath.substringAfterLast('/')
}

enum class DiffChangeType {
    ADD,
    MODIFY,
    DELETE,
    RENAME,
    COPY,
    TYPE_CHANGE,
    UNTRACKED,
    CONFLICT
}

data class GitDiffFileContent(
    val path: String,
    val oldContent: String?,
    val newContent: String?,
    val hunks: List<DiffHunk> = emptyList()
)

data class DiffHunk(
    val oldStart: Int,
    val oldLineCount: Int,
    val newStart: Int,
    val newLineCount: Int,
    val header: String,
    val lines: List<DiffLine>
)

data class DiffLine(
    val type: DiffLineType,
    val content: String,
    val oldNumber: Int? = null,
    val newNumber: Int? = null
)

enum class DiffLineType {
    CONTEXT,
    ADDED,
    REMOVED,
    HEADER
}
