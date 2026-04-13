package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MultiServerRepository(
    private val servers: List<McpServerConfig> = McpServerConfig.getAllServers()
) {
    private val clients = mutableMapOf<String, McpJsonRpcClient>()
    
    init {
        servers.forEach { server ->
            clients[server.serverId] = McpJsonRpcClient(server.baseUrl)
            Log.d(TAG, "✅ Registered client for ${server.serverId} at ${server.baseUrl}")
        }
    }
    
    suspend fun callTool(
        toolName: String,
        params: Map<String, Any?>
    ): McpToolData = withContext(Dispatchers.IO) {
        val server = servers.find { toolName in it.availableTools }
            ?: throw Exception("No server found for tool: $toolName")
        
        Log.d(TAG, "🔧 Routing $toolName to ${server.serverId}")
        
        val client = clients[server.serverId]
            ?: throw Exception("No client for ${server.serverId}")
        
        client.callTool(toolName, params)
    }
    
    suspend fun connectAndListTools(): McpConnectionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔗 Connecting to all MCP servers...")

            val connectionResults = connectAll()
            val allConnected = connectionResults.values.all { !it.startsWith("Error") }

            if (allConnected) {
                val tools = listAllTools().values.flatten().map { McpTool(it, "") }
                Log.d(TAG, "✅ All servers connected, received ${tools.size} tools")
                McpConnectionResult(
                    isConnected = true,
                    tools = tools,
                    error = null
                )
            } else {
                Log.e(TAG, "❌ Some servers failed to connect")
                McpConnectionResult(
                    isConnected = false,
                    tools = emptyList(),
                    error = connectionResults.values.filter { it.startsWith("Error") }.joinToString(", ")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ MCP connection failed", e)
            McpConnectionResult(
                isConnected = false,
                tools = emptyList(),
                error = e.message ?: "Unknown error"
            )
        }
    }

    suspend fun connectAll(): Map<String, String> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, String>()
        
        clients.forEach { (serverId, client) ->
            try {
                val result = client.initialize()
                results[serverId] = result
                Log.d(TAG, "✅ Connected to $serverId: $result")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to connect to $serverId", e)
                results[serverId] = "Error: ${e.message}"
            }
        }
        
        results
    }
    
    suspend fun listAllTools(): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, List<String>>()
        
        servers.forEach { server ->
            try {
                val client = clients[server.serverId] ?: return@forEach
                val tools = client.listTools()
                results[server.serverId] = tools.map { it.name }
            } catch (e: Exception) {
                results[server.serverId] = emptyList()
            }
        }
        
        results
    }
    
    companion object {
        private const val TAG = "MultiServerRepository"
    }
}
