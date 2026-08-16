package com.zeus.code.mcp

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class McpClient(
    private val config: McpServerConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        val reqId = UUID.randomUUID().toString()
        val requestBody = McpJsonRpcRequest(
            id = reqId,
            method = "tools/list",
            params = buildJsonObject {}
        )
        val response = callRpc(requestBody)
        if (response.error != null) {
            throw RuntimeException("MCP Error (${response.error.code}): ${response.error.message}")
        }
        val result = response.result?.let { json.decodeFromJsonElement<McpToolListResult>(it) }
        result?.tools?.map {
            it.copy(serverId = config.id, serverName = config.name)
        } ?: emptyList()
    }

    suspend fun callTool(name: String, arguments: JsonObject): McpCallToolResult = withContext(Dispatchers.IO) {
        val reqId = UUID.randomUUID().toString()
        val requestBody = McpJsonRpcRequest(
            id = reqId,
            method = "tools/call",
            params = buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            }
        )
        val response = callRpc(requestBody)
        if (response.error != null) {
            return@withContext McpCallToolResult(
                content = listOf(McpContentItem(type = "text", text = "Error (${response.error.code}): ${response.error.message}")),
                isError = true
            )
        }
        val result = response.result?.let { json.decodeFromJsonElement<McpCallToolResult>(it) }
        result ?: McpCallToolResult(
            content = listOf(McpContentItem(type = "text", text = "Empty tool response")),
            isError = false
        )
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val reqId = UUID.randomUUID().toString()
            val requestBody = McpJsonRpcRequest(
                id = reqId,
                method = "ping"
            )
            val res = callRpc(requestBody)
            res.error == null
        } catch (_: Exception) {
            false
        }
    }

    private fun callRpc(rpcRequest: McpJsonRpcRequest): McpJsonRpcResponse {
        val bodyString = json.encodeToString(McpJsonRpcRequest.serializer(), rpcRequest)
        val builder = Request.Builder()
            .url(config.serverUrl)
            .post(bodyString.toRequestBody(mediaType))

        config.headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        client.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return McpJsonRpcResponse(
                    error = McpJsonRpcError(code = resp.code, message = "HTTP ${resp.code}: $raw")
                )
            }
            return json.decodeFromString(McpJsonRpcResponse.serializer(), raw)
        }
    }
}
