package com.zeus.data.git.models

/**
 * Complete commit metadata for UI & git ops.
 */
data class GitCommitInfo(
    val id: String,
    val shortId: String,
    val fullMessage: String,
    val shortMessage: String,
    val authorName: String,
    val authorEmail: String,
    val authorTimeMillis: Long,
    val committerName: String,
    val committerEmail: String,
    val committerTimeMillis: Long,
    val parentIds: List<String> = emptyList(),
    val isMerge: Boolean = false
) {
    val isInitialCommit: Boolean
        get() = parentIds.isEmpty()
}

data class GitCommitList(
    val commits: List<GitCommitInfo>,
    val hasMore: Boolean = false
)

/**
 * Author identity wrapper for JGit commits
 */
data class GitAuthor(
    val name: String,
    val email: String
) {
    companion object {
        val DEFAULT = GitAuthor("Zeus User", "user@zeus.app")
    }
}

/**
 * Reset modes mapping to JGit ResetType
 */
enum class GitResetMode {
    SOFT,
    MIXED,
    HARD,
    KEEP,
    MERGE
}
