package com.zeus.data.git.models

import java.io.File

/**
 * Represents a locally stored repository in Zeus app sandbox.
 */
data class LocalRepo(
    val id: String,
    val name: String,
    val path: File,
    val remoteUrl: String? = null,
    val currentBranch: String? = null,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val lastCommitMessage: String? = null,
    val lastCommitTime: Long? = null,
    val isDirty: Boolean = false,
    val sizeBytes: Long = 0L,
    val aheadCount: Int = 0,
    val behindCount: Int = 0
) {
    val displayName: String
        get() = name

    val exists: Boolean
        get() = path.exists() && File(path, ".git").exists()

    companion object {
        fun fromDirectory(dir: File): LocalRepo {
            return LocalRepo(
                id = dir.name.hashCode().toString() + "_" + dir.name,
                name = dir.name,
                path = dir
            )
        }
    }
}

data class RepoImportResult(
    val repo: LocalRepo,
    val copiedFilesCount: Int,
    val skippedFilesCount: Int = 0
)

enum class RepoSortOrder {
    LAST_OPENED,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC
}
