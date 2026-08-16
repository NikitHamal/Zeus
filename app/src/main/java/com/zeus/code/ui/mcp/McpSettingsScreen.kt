package com.zeus.code.ui.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.code.mcp.McpManager
import com.zeus.code.mcp.McpServerConfig
import com.zeus.code.mcp.McpTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpSettingsScreen(
    mcpManager: McpManager,
    modifier: Modifier = Modifier
) {
    val servers by mcpManager.servers.collectAsState()
    val tools by mcpManager.availableTools.collectAsState()
    val isLoading by mcpManager.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MCP Tools & Servers", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "${servers.count { it.enabled }} active servers · ${tools.size} tools discovered",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { mcpManager.refreshAllTools() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Tools")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Server")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Servers (${servers.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Available Tools (${tools.size})") }
                )
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (selectedTab == 0) {
                if (servers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No MCP Servers Configured",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Connect Model Context Protocol (MCP) servers to equip Zeus agents with external tools and data sources.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add MCP Server")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(servers, key = { it.id }) { server ->
                            McpServerCard(
                                server = server,
                                onToggle = { enabled -> mcpManager.toggleServer(server.id, enabled) },
                                onDelete = { mcpManager.removeServer(server.id) }
                            )
                        }
                    }
                }
            } else {
                if (tools.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No tools found. Make sure your active MCP servers are reachable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(tools) { tool ->
                            McpToolCard(tool = tool)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, apiKey, autoApprove ->
                val headers = if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
                mcpManager.addServer(name, url, headers, autoApprove)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun McpServerCard(
    server: McpServerConfig,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = if (server.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(server.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            server.serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(checked = server.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun McpToolCard(tool: McpTool) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = tool.serverName.ifBlank { "MCP" },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = tool.name,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (tool.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, apiKey: String, autoApprove: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var autoApprove by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add MCP Server", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g. GitHub Tools, SQLite MCP") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server Endpoint URL") },
                    placeholder = { Text("https://mcp.example.com/sse") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Bearer Token / API Key (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoApprove, onCheckedChange = { autoApprove = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-approve tool execution", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, url, apiKey, autoApprove) },
                enabled = url.isNotBlank()
            ) {
                Text("Add Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
