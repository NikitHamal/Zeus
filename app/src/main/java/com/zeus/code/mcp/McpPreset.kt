package com.zeus.code.mcp

data class McpPreset(
    val name: String,
    val description: String,
    val defaultUrl: String,
    val category: String,
    val isOfficial: Boolean = true
)

object McpPresets {
    val POPULAR_PRESETS = listOf(
        McpPreset(
            name = "GitHub MCP",
            description = "Search repositories, read code files, inspect pull requests, and manage issues directly via MCP protocol.",
            defaultUrl = "https://api.github.com/mcp",
            category = "Development"
        ),
        McpPreset(
            name = "Web Scraper & Search MCP",
            description = "Live web search, website markdown extraction, and browser readability content parser.",
            defaultUrl = "https://mcp-web-search.zeus.internal/sse",
            category = "Search & Web"
        ),
        McpPreset(
            name = "SQLite Database MCP",
            description = "Inspect database schemas, query tables, run SQL commands, and examine data rows.",
            defaultUrl = "http://localhost:8080/mcp/sqlite",
            category = "Database"
        ),
        McpPreset(
            name = "Memory & Knowledge Graph MCP",
            description = "Store and retrieve persistent semantic entities, relationships, and context graph nodes.",
            defaultUrl = "https://mcp-memory.zeus.internal/sse",
            category = "Memory"
        ),
        McpPreset(
            name = "Shell & Terminal Runner MCP",
            description = "Execute shell commands, run test suites, and inspect terminal outputs in isolated workspaces.",
            defaultUrl = "http://localhost:8080/mcp/terminal",
            category = "System"
        )
    )
}
