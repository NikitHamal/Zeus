
package com.zeus.data.remote

import com.zeus.data.auth.AuthManager
import com.zeus.data.remote.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApi @Inject constructor(
    private val client: HttpClient,
    private val authManager: AuthManager
) {
    private suspend fun token(): String = authManager.getToken() ?: throw IllegalStateException("Not authenticated")

    private fun HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer ${'$'}{runCatching { kotlinx.coroutines.runBlocking { token() } }.getOrDefault("")}")
        header(HttpHeaders.Accept, "application/vnd.github+json")
    }

    suspend fun getUser(): GitHubUser {
        return client.get("https://api.github.com/user") { auth() }.body()
    }

    suspend fun listRepos(page: Int = 1): List<GitHubRepo> {
        return client.get("https://api.github.com/user/repos") {
            auth()
            parameter("per_page", 100)
            parameter("page", page)
            parameter("sort", "updated")
        }.body()
    }

    suspend fun getRepo(fullName: String): GitHubRepo {
        return client.get("https://api.github.com/repos/$fullName") { auth() }.body()
    }

    suspend fun createRepo(req: CreateRepoRequest): GitHubRepo {
        return client.post("https://api.github.com/user/repos") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun deleteRepo(fullName: String) {
        client.delete("https://api.github.com/repos/$fullName") { auth() }
    }

    suspend fun forkRepo(fullName: String): GitHubRepo {
        return client.post("https://api.github.com/repos/$fullName/forks") { auth() }.body()
    }

    suspend fun listBranches(fullName: String): List<GitHubBranch> {
        return client.get("https://api.github.com/repos/$fullName/branches") { auth() }.body()
    }

    suspend fun listCommits(fullName: String, branch: String = "main"): List<GitHubCommit> {
        return client.get("https://api.github.com/repos/$fullName/commits") {
            auth()
            parameter("sha", branch)
            parameter("per_page", 50)
        }.body()
    }

    suspend fun listContents(fullName: String, path: String = "", ref: String? = null): List<GitHubContent> {
        return client.get("https://api.github.com/repos/$fullName/contents/$path") {
            auth()
            if (ref != null) parameter("ref", ref)
        }.body()
    }

    suspend fun listPRs(fullName: String): List<PullRequest> {
        return client.get("https://api.github.com/repos/$fullName/pulls") {
            auth()
            parameter("state", "open")
        }.body()
    }

    suspend fun mergePR(fullName: String, number: Int): JsonObject {
        return client.put("https://api.github.com/repos/$fullName/pulls/$number/merge") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("commit_title" to "Merge PR #$number via Zeus"))
        }.body()
    }
}
