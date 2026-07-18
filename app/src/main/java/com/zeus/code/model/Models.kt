package com.zeus.code.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class GitHubUser(
    val id: Long,
    val login: String,
    val name: String? = null,
    val bio: String? = null,
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class RepoOwner(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String = ""
)

@Serializable
data class Repository(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val description: String? = null,
    val private: Boolean = false,
    val fork: Boolean = false,
    val owner: RepoOwner,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("open_issues_count") val openIssues: Int = 0,
    val language: String? = null,
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class Branch(
    val name: String,
    val protected: Boolean = false
)

@Serializable
data class PullUser(val login: String)

@Serializable
data class PullRef(
    val ref: String,
    val sha: String
)

@Serializable
data class PullRequest(
    val number: Int,
    val title: String,
    val state: String,
    val draft: Boolean = false,
    val user: PullUser,
    val head: PullRef,
    val base: PullRef,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = ""
)

@Serializable
data class PullRequestMarker(val url: String? = null)

@Serializable
data class Issue(
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    val user: PullUser,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("pull_request") val pullRequestMarker: PullRequestMarker? = null
)

@Serializable
data class GitHubError(val message: String = "Unknown GitHub error")

@Serializable
data class MergeResult(
    val sha: String? = null,
    val merged: Boolean = false,
    val message: String = ""
)

data class Workspace(
    val name: String,
    val path: String,
    val gitRepository: Boolean,
    val currentBranch: String? = null,
    val remoteUrl: String? = null,
    val modifiedAt: Long = 0L
) {
    val directory: File get() = File(path)
}

data class FileEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val size: Long,
    val depth: Int
)

data class GitStatusSummary(
    val branch: String,
    val added: Set<String> = emptySet(),
    val changed: Set<String> = emptySet(),
    val modified: Set<String> = emptySet(),
    val removed: Set<String> = emptySet(),
    val missing: Set<String> = emptySet(),
    val untracked: Set<String> = emptySet(),
    val conflicting: Set<String> = emptySet()
) {
    val clean: Boolean
        get() = added.isEmpty() && changed.isEmpty() && modified.isEmpty() && removed.isEmpty() &&
            missing.isEmpty() && untracked.isEmpty() && conflicting.isEmpty()

    val changeCount: Int
        get() = listOf(added, changed, modified, removed, missing, untracked, conflicting).sumOf { it.size }

    fun pretty(): String = buildString {
        appendLine("On branch $branch")
        if (clean) append("Working tree clean") else {
            if (conflicting.isNotEmpty()) appendLine("Conflicts: ${conflicting.joinToString()}")
            if (added.isNotEmpty()) appendLine("Added: ${added.joinToString()}")
            if (changed.isNotEmpty()) appendLine("Changed: ${changed.joinToString()}")
            if (modified.isNotEmpty()) appendLine("Modified: ${modified.joinToString()}")
            if (removed.isNotEmpty()) appendLine("Removed: ${removed.joinToString()}")
            if (missing.isNotEmpty()) appendLine("Missing: ${missing.joinToString()}")
            if (untracked.isNotEmpty()) appendLine("Untracked: ${untracked.joinToString()}")
        }
    }
}

data class CommitInfo(
    val hash: String,
    val shortHash: String,
    val message: String,
    val author: String,
    val timestamp: Long
)

data class DeviceLoginState(
    val code: String,
    val verificationUri: String,
    val expiresIn: Int
)

enum class MainTab { HOME, AGENT, WORKSPACES, GITHUB, TERMINAL, SETTINGS }
