package com.example.aiadventchallenge.domain.model.mcp

data class McpTool(
    val name: String,
    val description: String
)

data class McpConnectionResult(
    val isConnected: Boolean,
    val tools: List<McpTool>,
    val error: String? = null
)

enum class McpConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
