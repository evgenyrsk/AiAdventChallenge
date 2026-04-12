package com.example.mcp.server.registry

import com.example.mcp.server.security.CrossServerAcl
import com.example.mcp.server.security.McpInputValidator
import com.example.mcp.server.security.ValidationResult
import kotlinx.serialization.json.JsonElement

class McpToolRouter(
    private val registry: McpServerRegistry = McpServerRegistry,
    private val enableValidation: Boolean = true,
    private val enableAcl: Boolean = true
) {
    
    fun routeToolCall(
        toolName: String,
        params: JsonElement,
        sourceServerId: String? = null
    ): RoutingResult {
        if (enableValidation) {
            val validationResult = McpInputValidator.validateToolInput(toolName, params)
            if (validationResult is ValidationResult.Error) {
                return RoutingResult.Error(validationResult.message)
            }
        }
        
        val server = registry.getServerForTool(toolName)
            ?: return RoutingResult.Error("No server found for tool: $toolName")
        
        if (server.status != ServerStatus.HEALTHY) {
            return RoutingResult.Error("Server ${server.id} is not healthy (status: ${server.status})")
        }
        
        if (enableAcl && sourceServerId != null && sourceServerId != server.id) {
            val hasAccess = CrossServerAcl.checkAccess(
                sourceServerId = sourceServerId,
                targetServerId = server.id,
                toolName = toolName
            )
            
            if (!hasAccess) {
                return RoutingResult.Error(
                    "Access denied: server '$sourceServerId' is not allowed to call tool '$toolName' on server '${server.id}'"
                )
            }
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
        
        if (enableAcl && sourceServerId != null && sourceServerId != server.id) {
            return CrossServerAcl.checkAccess(
                sourceServerId = sourceServerId,
                targetServerId = server.id,
                toolName = toolName
            )
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
    
    fun setValidationEnabled(enabled: Boolean) {
        // For now, this is immutable at construction
        // In a more complex setup, this could be made dynamic
    }
    
    fun setAclEnabled(enabled: Boolean) {
        // For now, this is immutable at construction
        // In a more complex setup, this could be made dynamic
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
