package com.zeus.data.model

enum class GitStatusType {
    UNTRACKED, MODIFIED, ADDED, DELETED, RENAMED, CONFLICT, UNMODIFIED, IGNORED
}

data class FileTreeNode(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val children: List<FileTreeNode> = emptyList(),
    val gitStatus: GitStatusType = GitStatusType.UNMODIFIED,
    val size: Long = 0,
    val lastModified: Long = 0,
    val isExpanded: Boolean = false
)

data class EditorFileState(
    val path: String,
    val name: String,
    val content: String,
    val originalContent: String,
    val isDirty: Boolean = false,
    val isNew: Boolean = false,
    val language: String = "plaintext"
)

fun detectLanguage(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "js", "jsx", "mjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "py" -> "python"
        "go" -> "go"
        "rs" -> "rust"
        "swift" -> "swift"
        "cpp", "cc", "c", "h" -> "cpp"
        "md", "markdown" -> "markdown"
        "json" -> "json"
        "yaml", "yml" -> "yaml"
        "xml" -> "xml"
        "gradle" -> "gradle"
        "sh" -> "shell"
        else -> "plaintext"
    }
}
