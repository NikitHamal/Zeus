
package com.zeus.data.github

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApiService @Inject constructor(
    private val client: HttpClient
) {
    private val base = "https://api.github.com"

    suspend fun listPRs(owner: String, repo: String, state: String = "open"): List<PullRequest> {
        return client.get("$base/repos/$owner/$repo/pulls") {
            parameter("state", state)
            parameter("per_page", 50)
        }.body()
    }

    suspend fun getPR(owner: String, repo: String, number: Int): PullRequest {
        return client.get("$base/repos/$owner/$repo/pulls/$number").body()
    }

    suspend fun listPRFiles(owner: String, repo: String, number: Int): List<PRFile> {
        return client.get("$base/repos/$owner/$repo/pulls/$number/files").body()
    }

    suspend fun listComments(owner: String, repo: String, number: Int): List<PRComment> {
        return client.get("$base/repos/$owner/$repo/issues/$number/comments").body()
    }

    suspend fun postComment(owner: String, repo: String, number: Int, body: String): PRComment {
        return client.post("$base/repos/$owner/$repo/issues/$number/comments") {
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(body))
        }.body()
    }

    suspend fun mergePR(owner: String, repo: String, number: Int, method: String = "merge"): Map<String, String> {
        return client.put("$base/repos/$owner/$repo/pulls/$number/merge") {
            contentType(ContentType.Application.Json)
            setBody(MergePRRequest(merge_method = method))
        }.body()
    }

    suspend fun approvePR(owner: String, repo: String, number: Int): Map<String, String> {
        return client.post("$base/repos/$owner/$repo/pulls/$number/reviews") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("event" to "APPROVE"))
        }.body()
    }

    suspend fun createPR(owner: String, repo: String, req: CreatePRRequest): PullRequest {
        return client.post("$base/repos/$owner/$repo/pulls") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body()
    }

    suspend fun createRepo(name: String, description: String, private: Boolean): Map<String, Any> {
        return client.post("$base/user/repos") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("name" to name, "description" to description, "private" to private))
        }.body()
    }
}
