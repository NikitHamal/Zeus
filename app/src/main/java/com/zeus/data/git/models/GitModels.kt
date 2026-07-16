
package com.zeus.data.git.models

data class GitStatus(
    val untracked: List<String> = emptyList(),
    val modified: List<String> = emptyList(),
    val added: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val changed: List<String> = emptyList(),
    val conflicting: List<String> = emptyList(),
    val isClean: Boolean = false
)

data class LocalRepo(
    val name: String,
    val path: String,
    val currentBranch: String = "main",
    val remoteUrl: String? = null,
    val lastModified: Long = 0L
)

data class GitCommitLocal(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val email: String,
    val time: Long,
    val fullMessage: String
)

data class FileChange(
    val path: String,
    val changeType: String,
    val staged: Boolean = false
)
