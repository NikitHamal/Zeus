package com.zeus.code.mcp

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class McpManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("zeus_mcp_servers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _servers = MutableStateFlow<List<McpServerConfig>>(emptyList())
    val servers: StateFlow<List<McpServerConfig>> = _servers.asStateFlow()

    private val _availableTools = MutableStateFlow<List<McpTool>>(emptyList())
    val availableTools: StateFlow<List<McpTool>> = _availableTools.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadServers()
        refreshAllTools()
    }

    private fun loadServers() {
        val raw = prefs.getString("server_list", null)
        if (raw != null) {
            try {
                val list = json.decodeFromString<List<McpServerConfig>>(raw)
                _servers.value = list
            } catch (_: Exception) {
                _servers.value = emptyList()
            }
        }
    }

    private fun saveServers(list: List<McpServerConfig>) {
        _servers.value = list
        prefs.edit().putString("server_list", json.encodeToString(list)).apply()
    }

    fun addServer(name: String, url: String, headers: Map<String, String> = emptyMap(), autoApprove: Boolean = false) {
        val newServer = McpServerConfig(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "MCP Server" },
            serverUrl = url.trim(),
            headers = headers,
            autoApprove = autoApprove
        )
        val updated = _servers.value + newServer
        saveServers(updated)
        refreshAllTools()
    }

    fun updateServer(config: McpServerConfig) {
        val updated = _servers.value.map { if (it.id == config.id) config else it }
        saveServers(updated)
        refreshAllTools()
    }

    fun removeServer(serverId: String) {
        val updated = _servers.value.filterNot { it.id == serverId }
        saveServers(updated)
        refreshAllTools()
    }

    fun toggleServer(serverId: String, enabled: Boolean) {
        val updated = _servers.value.map { if (it.id == serverId) it.copy(enabled = enabled) else it }
        saveServers(updated)
        refreshAllTools()
    }

    fun refreshAllTools() {
        scope.launch {
            _isLoading.value = true
            val tools = mutableListOf<McpTool>()
            for (server in _servers.value) {
                if (!server.enabled) continue
                try {
                    val client = McpClient(server)
                    val serverTools = client.listTools()
                    tools.addAll(serverTools)
                } catch (_: Exception) {
                    // Server might be temporarily unreachable
                }
            }
            _availableTools.value = tools
            _isLoading.value = false
        }
    }

    suspend fun executeTool(toolName: String, serverId: String, arguments: JsonObject): McpCallToolResult = withContext(Dispatchers.IO) {
        val server = _servers.value.find { it.id == serverId }
            ?: return@withContext McpCallToolResult(
                content = listOf(McpContentItem(type = "text", text = "Server not found")),
                isError = true
            )
        val client = McpClient(server)
        client.callTool(toolName, arguments)
    }
}
