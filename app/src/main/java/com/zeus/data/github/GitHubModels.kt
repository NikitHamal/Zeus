
package com.zeus.data.github

import kotlinx.serialization.Serializable

@Serializable
data class PullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String, // open, closed, merged
    val user: GHUser,
    val head: PRRef,
    val base: PRRef,
    val created_at: String,
    val updated_at: String,
    val merged_at: String? = null,
    val mergeable: Boolean? = null,
    val html_url: String,
    val diff_url: String? = null,
    val comments: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changed_files: Int = 0
)

@Serializable
data class PRRef(
    val label: String,
    val ref: String,
    val sha: String,
    val repo: GHRepoRef? = null
)

@Serializable
data class GHRepoRef(val id: Long, val full_name: String)

@Serializable
data class GHUser(
    val id: Long,
    val login: String,
    val avatar_url: String,
    val html_url: String
)

@Serializable
data class PRComment(
    val id: Long,
    val user: GHUser,
    val body: String,
    val created_at: String,
    val html_url: String
)

@Serializable
data class PRFile(
    val sha: String,
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String? = null
)

@Serializable
data class CreatePRRequest(
    val title: String,
    val body: String? = null,
    val head: String,
    val base: String,
    val draft: Boolean = false
)

@Serializable
data class CreateCommentRequest(
    val body: String
)

@Serializable
data class MergePRRequest(
    val commit_title: String? = null,
    val commit_message: String? = null,
    val merge_method: String = "merge" // merge, squash, rebase
)
