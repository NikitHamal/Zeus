package com.zeus.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val id: Long,
    val avatarUrl: String = "",
    val name: String? = null,
    val bio: String? = null,
    val publicRepos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: GitHubUser? = null,
    val description: String? = null,
    val private: Boolean = false,
    val fork: Boolean = false,
    val stars: Int = 0,
    val forks: Int = 0,
    val language: String? = null,
    val updatedAt: String? = null,
    val defaultBranch: String = "main",
    val htmlUrl: String = "",
    val cloneUrl: String = ""
)

enum class RepoSource { LOCAL, REMOTE, STARRED }

data class LocalRepo(
    val id: String = "",
    val name: String,
    val path: String,
    val remoteUrl: String? = null,
    val lastOpened: Long = System.currentTimeMillis(),
    val currentBranch: String = "main",
    val isDirty: Boolean = false,
    val ahead: Int = 0,
    val behind: Int = 0,
    val description: String? = null
)

data class UnifiedRepo(
    val local: LocalRepo? = null,
    val remote: GitHubRepo? = null,
    val source: RepoSource,
    val isStarred: Boolean = false
) {
    val displayName: String get() = local?.name ?: remote?.name ?: ""
    val displayDescription: String? get() = local?.description ?: remote?.description
}
