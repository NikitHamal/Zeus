package com.zeus.code.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val serverUrl: String,
    val transport: String = "sse", // "sse" or "http"
    val enabled: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val autoApprove: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class McpJsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonObject? = null
)

@Serializable
data class McpJsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: JsonElement? = null,
    val error: McpJsonRpcError? = null
)

@Serializable
data class McpJsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class McpTool(
    val name: String,
    val description: String = "",
    val inputSchema: JsonObject? = null,
    val serverId: String = "",
    val serverName: String = ""
)

@Serializable
data class McpToolListResult(
    val tools: List<McpTool> = emptyList(),
    val nextCursor: String? = null
)

@Serializable
data class McpCallToolResult(
    val content: List<McpContentItem> = emptyList(),
    val isError: Boolean = false
)

@Serializable
data class McpContentItem(
    val type: String, // "text", "image", "resource"
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)
