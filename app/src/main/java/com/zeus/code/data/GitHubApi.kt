package com.zeus.code.data

import com.zeus.code.model.AccessTokenResponse
import com.zeus.code.model.ArtifactsResponse
import com.zeus.code.model.Branch
import com.zeus.code.model.DeviceCodeResponse
import com.zeus.code.model.GitHubError
import com.zeus.code.model.GitHubUser
import com.zeus.code.model.Issue
import com.zeus.code.model.MergeResult
import com.zeus.code.model.PullRequest
import com.zeus.code.model.Repository
import com.zeus.code.model.RunJobsResponse
import com.zeus.code.model.WorkflowRunsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class GitHubApiException(val statusCode: Int, override val message: String) : RuntimeException(message)

class GitHubApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val apiBase = "https://api.github.com"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun requestDeviceCode(clientId: String): DeviceCodeResponse = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) { "OAUTH_CLIENT_ID is missing from the build." }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "repo read:user user:email workflow delete_repo gist notifications")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()
        execute(request) { json.decodeFromString(it) }
    }

    suspend fun pollForToken(clientId: String, device: DeviceCodeResponse): String = withContext(Dispatchers.IO) {
        var interval = device.interval.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + device.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("device_code", device.deviceCode)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .build()
            val request = Request.Builder()
                .url("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .post(body)
                .build()
            val response = execute(request) { json.decodeFromString<AccessTokenResponse>(it) }
            response.accessToken?.let { return@withContext it }
            when (response.error) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("The GitHub device code expired. Start login again.")
                "access_denied" -> error("GitHub authorization was cancelled.")
                null -> Unit
                else -> error(response.errorDescription ?: response.error)
            }
        }
        error("The GitHub device authorization timed out.")
    }

    suspend fun user(token: String): GitHubUser = get(token, "/user")

    suspend fun repositories(token: String): List<Repository> =
        paged(token, "/user/repos?sort=updated&affiliation=owner,collaborator,organization_member")

    suspend fun createRepository(
        token: String,
        name: String,
        description: String,
        private: Boolean,
        autoInit: Boolean
    ): Repository = post(
        token,
        "/user/repos",
        buildJsonObject {
            put("name", name)
            put("description", description)
            put("private", private)
            put("auto_init", autoInit)
        }.toString()
    )

    suspend fun deleteRepository(token: String, owner: String, repo: String) {
        request<Unit>(token, "DELETE", "/repos/$owner/$repo") { Unit }
    }

    suspend fun forkRepository(token: String, owner: String, repo: String): Repository =
        post(token, "/repos/$owner/$repo/forks", "{}")

    suspend fun branches(token: String, owner: String, repo: String): List<Branch> =
        paged(token, "/repos/$owner/$repo/branches")

    suspend fun pulls(token: String, owner: String, repo: String): List<PullRequest> =
        paged(token, "/repos/$owner/$repo/pulls?state=all")

    suspend fun issues(token: String, owner: String, repo: String): List<Issue> =
        paged<Issue>(token, "/repos/$owner/$repo/issues?state=all")
            .filter { it.pullRequestMarker == null }

    suspend fun createIssue(token: String, owner: String, repo: String, title: String, body: String): Issue =
        post(
            token,
            "/repos/$owner/$repo/issues",
            buildJsonObject { put("title", title); put("body", body) }.toString()
        )

    suspend fun createPullRequest(
        token: String,
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String
    ): PullRequest = post(
        token,
        "/repos/$owner/$repo/pulls",
        buildJsonObject {
            put("title", title)
            put("head", head)
            put("base", base)
            put("body", body)
        }.toString()
    )

    suspend fun mergePullRequest(
        token: String,
        owner: String,
        repo: String,
        number: Int,
        method: String = "merge"
    ): MergeResult = put(
        token,
        "/repos/$owner/$repo/pulls/$number/merge",
        buildJsonObject { put("merge_method", method) }.toString()
    )

    suspend fun reviewPullRequest(
        token: String,
        owner: String,
        repo: String,
        number: Int,
        body: String,
        event: String
    ) {
        request<Unit>(
            token,
            "POST",
            "/repos/$owner/$repo/pulls/$number/reviews",
            buildJsonObject { put("body", body); put("event", event) }.toString()
        ) { Unit }
    }

    /* ----------------------------------------------------------------- */
    /* Actions — workflow runs, jobs and downloadable artifacts          */
    /* ----------------------------------------------------------------- */

    suspend fun workflowRuns(
        token: String,
        owner: String,
        repo: String,
        perPage: Int = 20
    ) = get<WorkflowRunsResponse>(token, "/repos/$owner/$repo/actions/runs?per_page=$perPage").workflowRuns

    suspend fun runJobs(token: String, owner: String, repo: String, runId: Long) =
        get<RunJobsResponse>(token, "/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=50").jobs

    suspend fun runArtifacts(token: String, owner: String, repo: String, runId: Long) =
        get<ArtifactsResponse>(token, "/repos/$owner/$repo/actions/runs/$runId/artifacts?per_page=50").artifacts

    /** Streams an artifact ZIP to [output]. GitHub redirects to a signed URL; OkHttp follows. */
    suspend fun downloadArchive(token: String, downloadUrl: String, output: OutputStream) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Zeus-Android")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw GitHubApiException(response.code, "Download failed (${response.code})")
                val body = response.body ?: throw GitHubApiException(502, "Empty download response")
                body.byteStream().use { it.copyTo(output) }
            }
        } finally {
            output.close()
        }
    }

    private suspend inline fun <reified T> paged(token: String, path: String): List<T> {
        val output = mutableListOf<T>()
        for (page in 1..20) {
            val separator = if ('?' in path) '&' else '?'
            val batch: List<T> = get(token, "$path${separator}per_page=100&page=$page")
            output += batch
            if (batch.size < 100) break
        }
        return output
    }

    private suspend inline fun <reified T> get(token: String, path: String): T =
        request(token, "GET", path) { json.decodeFromString(it) }

    private suspend inline fun <reified T> post(token: String, path: String, body: String): T =
        request(token, "POST", path, body) { json.decodeFromString(it) }

    private suspend inline fun <reified T> put(token: String, path: String, body: String): T =
        request(token, "PUT", path, body) { json.decodeFromString(it) }

    private suspend fun <T> request(
        token: String,
        method: String,
        path: String,
        body: String? = null,
        transform: (String) -> T
    ): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(apiBase + path)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Zeus-Android")
        when (method) {
            "GET" -> builder.get()
            "DELETE" -> builder.delete()
            else -> builder.method(method, (body ?: "{}").toRequestBody(jsonMediaType))
        }
        execute(builder.build(), transform)
    }

    private fun <T> execute(request: Request, transform: (String) -> T): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { json.decodeFromString<GitHubError>(body).message }
                    .getOrDefault("GitHub request failed (${response.code})")
                throw GitHubApiException(response.code, message)
            }
            return transform(body)
        }
    }
}
