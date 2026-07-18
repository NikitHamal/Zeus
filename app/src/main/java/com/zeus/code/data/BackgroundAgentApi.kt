package com.zeus.code.data

import com.zeus.code.BuildConfig
import com.zeus.code.model.AgentBranchesResponse
import com.zeus.code.model.AgentEventsResponse
import com.zeus.code.model.AgentMeResponse
import com.zeus.code.model.AgentPairStartResponse
import com.zeus.code.model.AgentProjectResponse
import com.zeus.code.model.AgentRepositoriesResponse
import com.zeus.code.model.AgentSessionResponse
import com.zeus.code.model.AgentSimpleResponse
import com.zeus.code.model.AgentStateResponse
import com.zeus.code.model.AgentTokenResponse
import com.zeus.code.model.AgentUpload
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BackgroundAgentApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String
) : Exception(message)

class BackgroundAgentApi {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val origin = BuildConfig.BACKGROUND_AGENT_BASE_URL.trimEnd('/')
    private val originUri = URI(origin)
    private val base = origin + "/api/background-agent/mobile"

    suspend fun startPairing(deviceName: String): AgentPairStartResponse = postJson(
        path = "/pair/start/",
        body = buildJsonObject { put("deviceName", deviceName) }.toString(),
        token = null
    )

    suspend fun pollPairing(deviceCode: String): AgentTokenResponse = postJson(
        path = "/pair/token/",
        body = buildJsonObject { put("deviceCode", deviceCode) }.toString(),
        token = null
    )

    suspend fun me(token: String): AgentMeResponse = get("/me/", token)

    suspend fun revoke(token: String): AgentSimpleResponse = postJson("/revoke/", "{}", token)

    suspend fun state(token: String, archived: Boolean): AgentStateResponse =
        get("/state/?archived=${if (archived) 1 else 0}", token)

    suspend fun repositories(token: String, query: String = ""): AgentRepositoriesResponse =
        get("/repositories/?perPage=100&q=${encode(query)}", token)

    suspend fun branches(token: String, repository: String): AgentBranchesResponse =
        get("/branches/?repo=${encode(repository)}", token)

    suspend fun addProject(token: String, repository: String, baseBranch: String): AgentProjectResponse = postJson(
        path = "/projects/",
        body = buildJsonObject {
            put("repoFullName", repository)
            put("baseBranch", baseBranch)
        }.toString(),
        token = token
    )

    suspend fun createSession(
        token: String,
        projectId: String,
        goal: String,
        sourceBranch: String,
        files: List<AgentUpload>
    ): AgentSessionResponse = multipart(
        path = "/sessions/",
        token = token,
        fields = mapOf("projectId" to projectId, "goal" to goal, "sourceBranch" to sourceBranch),
        files = files
    )

    suspend fun session(token: String, id: String): AgentSessionResponse = get("/sessions/$id/", token)

    suspend fun events(token: String, id: String, after: Long): AgentEventsResponse =
        get("/sessions/$id/events/?after=$after", token)

    suspend fun sendMessage(
        token: String,
        id: String,
        content: String,
        files: List<AgentUpload>
    ): AgentSimpleResponse = multipart(
        path = "/sessions/$id/messages/",
        token = token,
        fields = mapOf("content" to content),
        files = files
    )

    suspend fun control(token: String, id: String, command: String): AgentSessionResponse = postJson(
        path = "/sessions/$id/control/",
        body = buildJsonObject { put("command", command) }.toString(),
        token = token
    )

    suspend fun action(token: String, id: String, action: String): AgentSimpleResponse = postJson(
        path = "/sessions/$id/actions/",
        body = buildJsonObject { put("action", action) }.toString(),
        token = token
    )

    suspend fun lifecycle(token: String, id: String, action: String): AgentSessionResponse = postJson(
        path = "/sessions/$id/lifecycle/",
        body = buildJsonObject { put("action", action) }.toString(),
        token = token
    )

    suspend fun deleteSession(token: String, id: String): AgentSimpleResponse = request(
        Request.Builder().url(url("/sessions/$id/lifecycle/")).delete().authorized(token).build()
    )

    suspend fun download(token: String, downloadUrl: String, output: OutputStream): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url(downloadUrl)).get().authorized(token).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw parseError(response.code, response.body?.string().orEmpty())
            val body = response.body ?: throw BackgroundAgentApiException(502, "empty_response", "The artifact response was empty")
            body.byteStream().use { input -> input.copyTo(output) }
        }
    }

    private suspend inline fun <reified T> get(path: String, token: String): T = request(
        Request.Builder().url(url(path)).get().authorized(token).build()
    )

    private suspend inline fun <reified T> postJson(path: String, body: String, token: String?): T {
        val builder = Request.Builder()
            .url(url(path))
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
        if (token != null) builder.authorized(token)
        return request(builder.build())
    }

    private suspend inline fun <reified T> multipart(
        path: String,
        token: String,
        fields: Map<String, String>,
        files: List<AgentUpload>
    ): T {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (key, value) -> addFormDataPart(key, value) }
            files.forEach { upload ->
                addFormDataPart(
                    "files",
                    upload.name,
                    upload.bytes.toRequestBody(upload.contentType.toMediaTypeOrNull())
                )
            }
        }.build()
        val httpRequest = Request.Builder().url(url(path)).post(body).authorized(token).build()
        return request(httpRequest)
    }

    private suspend inline fun <reified T> request(request: Request): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw parseError(response.code, raw)
            try {
                json.decodeFromString<T>(raw)
            } catch (error: Exception) {
                throw BackgroundAgentApiException(502, "invalid_response", "NEBians returned an unreadable response")
            }
        }
    }

    private fun parseError(status: Int, raw: String): BackgroundAgentApiException {
        val payload = runCatching { json.decodeFromString<AgentSimpleResponse>(raw) }.getOrNull()
        val message = payload?.error?.takeIf { it.isNotBlank() }
            ?: when (status) {
                401 -> "Authorization expired. Connect Zeus again."
                403 -> "This device is not allowed to perform that action."
                404 -> "The requested background-agent item no longer exists."
                409 -> "That action is not available in the current task state."
                428 -> "Waiting for authorization in NEBians."
                429 -> "Too many requests. Wait a moment and try again."
                else -> "NEBians request failed ($status)."
            }
        return BackgroundAgentApiException(status, payload?.code.orEmpty(), message)
    }

    private fun Request.Builder.authorized(token: String) = header("Authorization", "Bearer $token")

    private fun url(path: String): String {
        if (!path.startsWith("http://") && !path.startsWith("https://")) {
            return if (path.startsWith("/api/")) origin + path else base + if (path.startsWith('/')) path else "/$path"
        }
        val target = runCatching { URI(path) }.getOrElse { throw BackgroundAgentApiException(400, "invalid_url", "NEBians returned an invalid download URL") }
        val sameOrigin = target.scheme.equals(originUri.scheme, true) &&
            target.host.equals(originUri.host, true) && effectivePort(target) == effectivePort(originUri)
        if (!sameOrigin) throw BackgroundAgentApiException(403, "untrusted_url", "NEBians returned an untrusted download URL")
        return target.toString()
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
