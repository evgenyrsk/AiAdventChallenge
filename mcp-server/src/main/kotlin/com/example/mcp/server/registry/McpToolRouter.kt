package com.example.mcp.server.registry

import kotlinx.serialization.json.JsonElement

class McpToolRouter(
    private val registry: McpServerRegistry = McpServerRegistry
) {

    fun routeToolCall(
        toolName: String,
        params: JsonElement,
        sourceServerId: String? = null
    ): RoutingResult {
        val server = registry.getServerForTool(toolName)
            ?: return RoutingResult.Error("No server found for tool: $toolName")

        if (server.status != ServerStatus.HEALTHY) {
            return RoutingResult.Error("Server ${server.id} is not healthy (status: ${server.status})")
        }

        return RoutingResult.Success(
            server = server,
            toolName = toolName,
            params = params
        )
    }

    fun canExecuteTool(toolName: String): Boolean {
        val server = registry.getServerForTool(toolName) ?: return false
        return server.status == ServerStatus.HEALTHY
    }

    fun canExecuteTool(
        toolName: String,
        sourceServerId: String? = null
    ): Boolean {
        val server = registry.getServerForTool(toolName) ?: return false

        if (server.status != ServerStatus.HEALTHY) {
            return false
        }

        return true
    }
    
    fun getToolLocation(toolName: String): ToolLocation? {
        val server = registry.getServerForTool(toolName) ?: return null
        return ToolLocation(
            serverId = server.id,
            serverName = server.name,
            baseUrl = server.baseUrl,
            isHealthy = server.status == ServerStatus.HEALTHY
        )
    }
    
    fun getAllToolLocations(): Map<String, ToolLocation> {
        val result = mutableMapOf<String, ToolLocation>()
        registry.getAllTools().forEach { (toolName, serverId) ->
            registry.getServerById(serverId)?.let { server ->
                result[toolName] = ToolLocation(
                    serverId = server.id,
                    serverName = server.name,
                    baseUrl = server.baseUrl,
                    isHealthy = server.status == ServerStatus.HEALTHY
                )
            }
        }
        return result
    }
}

sealed class RoutingResult {
    data class Success(
        val server: McpServer,
        val toolName: String,
        val params: JsonElement
    ) : RoutingResult()
    
    data class Error(val message: String) : RoutingResult()
}

data class ToolLocation(
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    val isHealthy: Boolean
)
