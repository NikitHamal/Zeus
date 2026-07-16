
package com.zeus.data.remote.models

import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String = "",
    val id: Long = 0,
    val avatar_url: String = "",
    val name: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val public_repos: Int = 0,
    val followers: Int = 0,
    val following: Int = 0
)

@Serializable
data class GitHubRepo(
    val id: Long = 0,
    val name: String = "",
    val full_name: String = "",
    val private: Boolean = false,
    val description: String? = null,
    val fork: Boolean = false,
    val html_url: String = "",
    val clone_url: String = "",
    val default_branch: String = "main",
    val stargazers_count: Int = 0,
    val forks_count: Int = 0,
    val open_issues_count: Int = 0,
    val updated_at: String = "",
    val owner: GitHubUser? = null,
    val language: String? = null
)

@Serializable
data class GitHubBranch(
    val name: String,
    val commit: CommitRef
)

@Serializable
data class CommitRef(val sha: String, val url: String)

@Serializable
data class GitHubCommit(
    val sha: String,
    val commit: CommitDetail,
    val author: GitHubUser? = null,
    val html_url: String = ""
)

@Serializable
data class CommitDetail(
    val message: String,
    val author: CommitAuthor,
    val committer: CommitAuthor? = null
)

@Serializable
data class CommitAuthor(val name: String, val email: String, val date: String)

@Serializable
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val `private`: Boolean = false,
    val auto_init: Boolean = true
)

@Serializable
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val type: String, // file or dir
    val download_url: String? = null
)

@Serializable
data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val user: GitHubUser,
    val html_url: String,
    val body: String? = null
)
