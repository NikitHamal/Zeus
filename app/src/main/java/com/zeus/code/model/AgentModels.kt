package com.zeus.code.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentPairStartResponse(
    val ok: Boolean = false,
    val deviceCode: String = "",
    val userCode: String = "",
    val verificationUrl: String = "",
    val verificationUrlComplete: String = "",
    val expiresIn: Int = 600,
    val interval: Int = 3,
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentTokenResponse(
    val ok: Boolean = false,
    val accessToken: String = "",
    val tokenType: String = "Bearer",
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentUser(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val photoUrl: String = ""
)

@Serializable
data class AgentGithub(
    val connected: Boolean = false,
    val login: String = "",
    val avatarUrl: String = ""
)

@Serializable
data class AgentMeResponse(
    val ok: Boolean = false,
    val user: AgentUser = AgentUser(),
    val github: AgentGithub = AgentGithub(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentProject(
    val id: String = "",
    val repoFullName: String = "",
    val repoHtmlUrl: String = "",
    val cloneUrl: String = "",
    val defaultBranch: String = "main",
    val preferredBaseBranch: String = "main",
    val private: Boolean = false,
    val status: String = "new",
    val lastSyncedAt: Long = 0L,
    val lastError: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class AgentRepository(
    val id: Long = 0L,
    val name: String = "",
    val fullName: String = "",
    val owner: String = "",
    val description: String = "",
    val private: Boolean = false,
    val archived: Boolean = false,
    val defaultBranch: String = "main",
    val htmlUrl: String = "",
    val cloneUrl: String = "",
    val updatedAt: String = "",
    val canPush: Boolean = false
)

@Serializable
data class AgentBranch(
    val name: String = "",
    val sha: String = ""
)

@Serializable
data class AgentContext(
    val estimatedTokens: Int = 0,
    val windowTokens: Int = 131072,
    val percent: Int = 0,
    val compactions: Int = 0
)

@Serializable
data class AgentAttachment(
    val id: String = "",
    val name: String = "",
    val kind: String = "document",
    val contentType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val extension: String = ""
)

@Serializable
data class AgentMessage(
    val id: String = "",
    val role: String = "assistant",
    val content: String = "",
    val label: String = "",
    val attachments: List<AgentAttachment> = emptyList(),
    val createdAt: Long = 0L
)

@Serializable
data class AgentEvent(
    val id: Long = 0L,
    val type: String = "",
    val message: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class AgentAction(
    val id: String = "",
    val action: String = "",
    val status: String = "",
    val error: String = "",
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L
)

@Serializable
data class AgentArtifact(
    val kind: String = "",
    val fileName: String = "",
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val downloadUrl: String = ""
)

@Serializable
data class AgentSession(
    val id: String = "",
    val projectId: String = "",
    val repoFullName: String = "",
    val repoHtmlUrl: String = "",
    val cloneUrl: String = "",
    val title: String = "",
    val goal: String = "",
    val sourceBranch: String = "main",
    val workBranch: String = "",
    val baseSha: String = "",
    val headSha: String = "",
    val status: String = "queued",
    val progress: Int = 0,
    val progressLabel: String = "Queued",
    val iteration: Int = 0,
    val maxIterations: Int = 0,
    val summary: String = "",
    val testSummary: String = "",
    val lastError: String = "",
    val archived: Boolean = false,
    val archivedAt: Long = 0L,
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long = 0L,
    val context: AgentContext = AgentContext(),
    val changedFiles: List<String> = emptyList(),
    val diff: String = "",
    val messages: List<AgentMessage> = emptyList(),
    val events: List<AgentEvent> = emptyList(),
    val actions: List<AgentAction> = emptyList(),
    val artifacts: Map<String, AgentArtifact> = emptyMap()
)

@Serializable
data class AgentWorker(
    val online: Int = 0,
    val healthy: Boolean = false
)

@Serializable
data class AgentModelState(
    val provider: String = "qwen",
    val model: String = "qwen3.7-plus",
    val label: String = "Qwen 3.7 Plus",
    val configured: Boolean = false
)

@Serializable
data class AgentStateResponse(
    val ok: Boolean = false,
    val projects: List<AgentProject> = emptyList(),
    val sessions: List<AgentSession> = emptyList(),
    val worker: AgentWorker = AgentWorker(),
    val model: AgentModelState = AgentModelState(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentRepositoriesResponse(
    val ok: Boolean = false,
    val repositories: List<AgentRepository> = emptyList(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentBranchesResponse(
    val ok: Boolean = false,
    val defaultBranch: String = "main",
    val branches: List<AgentBranch> = emptyList(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentProjectResponse(
    val ok: Boolean = false,
    val project: AgentProject = AgentProject(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentSessionResponse(
    val ok: Boolean = false,
    val session: AgentSession = AgentSession(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentSessionsResponse(
    val ok: Boolean = false,
    val sessions: List<AgentSession> = emptyList(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentEventsResponse(
    val ok: Boolean = false,
    val session: AgentSession = AgentSession(),
    val events: List<AgentEvent> = emptyList(),
    val actions: List<AgentAction> = emptyList(),
    val artifacts: Map<String, AgentArtifact> = emptyMap(),
    val error: String? = null,
    val code: String? = null
)

@Serializable
data class AgentSimpleResponse(
    val ok: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
    val code: String? = null
)

data class AgentPairingState(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val verificationUrlComplete: String,
    val expiresAt: Long,
    val intervalSeconds: Int
)

data class AgentUpload(
    val name: String,
    val contentType: String,
    val bytes: ByteArray
)
