
package com.zeus.data.git.models

data class GitBranch(
    val name: String,
    val shortName: String,
    val isRemote: Boolean,
    val isCurrent: Boolean,
    val commitId: String,
    val trackingBranch: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0
)

data class GitLogFilter(
    val query: String = "",
    val author: String? = null,
    val since: Long? = null,
    val path: String? = null,
    val branch: String? = null
)
