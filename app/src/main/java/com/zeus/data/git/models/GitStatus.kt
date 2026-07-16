package com.zeus.data.git.models

/**
 * Aggregated git status similar to `git status --porcelain` but structured.
 * Production model for UI rendering in Zeus.
 */
data class GitStatus(
    val isClean: Boolean,
    val branch: String,
    val remoteTrackingBranch: String? = null,
    val aheadCount: Int = 0,
    val behindCount: Int = 0,

    val stagedChanges: List<GitFileChange> = emptyList(),
    val unstagedChanges: List<GitFileChange> = emptyList(),
    val untrackedFiles: List<GitFileChange> = emptyList(),
    val conflictedFiles: List<GitFileChange> = emptyList(),
    val ignoredFiles: List<String> = emptyList(),

    val hasConflicts: Boolean = conflictedFiles.isNotEmpty()
) {
    val totalChanges: Int
        get() = stagedChanges.size + unstagedChanges.size + untrackedFiles.size + conflictedFiles.size

    val allChanges: List<GitFileChange>
        get() = stagedChanges + unstagedChanges + untrackedFiles + conflictedFiles

    companion object {
        fun clean(branch: String): GitStatus = GitStatus(
            isClean = true,
            branch = branch
        )
    }
}

/**
 * Result types for pull/merge/rebase.
 */
data class GitPullResult(
    val isSuccessful: Boolean,
    val isAlreadyUpToDate: Boolean = false,
    val mergeResult: GitMergeResult? = null,
    val fetchedFrom: String? = null
)

data class GitMergeResult(
    val isSuccessful: Boolean,
    val status: MergeStatus,
    val mergedCommits: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val message: String? = null
)

enum class MergeStatus {
    FAST_FORWARD,
    MERGED,
    CONFLICTING,
    ALREADY_UP_TO_DATE,
    FAILED,
    ABORTED,
    CHECKOUT_CONFLICT
}

data class GitRebaseResult(
    val isSuccessful: Boolean,
    val status: RebaseStatus,
    val conflicts: List<String> = emptyList(),
    val currentCommit: String? = null
)

enum class RebaseStatus {
    OK,
    CONFLICTS,
    FAST_FORWARD,
    ABORTED,
    STOPPED,
    FAILED,
    NOTHING_TO_COMMIT,
    ALREADY_UP_TO_DATE
}

data class GitOperationProgress(
    val taskName: String,
    val percent: Int, // 0..100
    val message: String? = null
)
