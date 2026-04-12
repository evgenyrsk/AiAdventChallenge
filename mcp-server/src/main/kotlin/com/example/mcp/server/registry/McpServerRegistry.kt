package com.example.mcp.server.registry

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

object McpServerRegistry {
    private val servers = ConcurrentHashMap<String, McpServer>()
    private val json = Json { ignoreUnknownKeys = true }
    
    fun registerServer(server: McpServer) {
        servers[server.id] = server
    }
    
    fun unregisterServer(serverId: String) {
        servers.remove(serverId)
    }
    
    fun getServerById(serverId: String): McpServer? {
        return servers[serverId]
    }
    
    fun getAllServers(): List<McpServer> {
        return servers.values.toList()
    }
    
    fun getServerForTool(toolName: String): McpServer? {
        return servers.values.find { it.tools.contains(toolName) }
    }
    
    fun getAllTools(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        servers.values.forEach { server ->
            server.tools.forEach { tool ->
                result[tool] = server.id
            }
        }
        return result
    }
    
    fun updateServerStatus(serverId: String, status: ServerStatus) {
        servers[serverId]?.let { server ->
            servers[serverId] = server.copy(
                status = status,
                lastHealthCheck = System.currentTimeMillis()
            )
        }
    }
    
    fun getHealthyServers(): List<McpServer> {
        return servers.values.filter { it.status == ServerStatus.HEALTHY }
    }
    
    fun clear() {
        servers.clear()
    }
    
    fun toJson(): String {
        return json.encodeToString(servers.values.toList())
    }
}
