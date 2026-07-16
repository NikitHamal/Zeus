package com.zeus.data.git.models

/**
 * Represents a single file change in git status or diff.
 */
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    UNTRACKED,
    CONFLICTED,
    TYPE_CHANGED,
    IGNORED
}

enum class StagedState {
    STAGED,
    UNSTAGED,
    BOTH,
    UNTRACKED,
    CONFLICTED
}

data class GitFileChange(
    val path: String,
    val oldPath: String? = null, // for renames
    val changeType: ChangeType,
    val stagedState: StagedState,
    val isStaged: Boolean = false
) {
    val fileName: String
        get() = path.substringAfterLast('/')

    val isConflicted: Boolean
        get() = stagedState == StagedState.CONFLICTED
}
