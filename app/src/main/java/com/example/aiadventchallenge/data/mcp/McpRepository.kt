package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import com.example.aiadventchallenge.domain.mcp.McpToolData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class McpRepository(
    private val client: McpJsonRpcClient
) {
    private var isConnected = false
    
    suspend fun connectAndListTools(): McpConnectionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔗 Connecting to MCP server...")
            
            val initMessage = client.initialize()
            Log.d(TAG, "✅ Initialized: $initMessage")
            
            isConnected = true
            
            val tools = client.listTools()
            Log.d(TAG, "📦 Received ${tools.size} tools")
            
            tools.forEach { tool ->
                Log.d(TAG, "   - ${tool.name}: ${tool.description}")
            }
            
            McpConnectionResult(
                isConnected = true,
                tools = tools,
                error = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ MCP connection failed", e)
            isConnected = false
            
            McpConnectionResult(
                isConnected = false,
                tools = emptyList(),
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    suspend fun callTool(
        name: String,
        params: Map<String, Any?>
    ): McpToolData = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔧 Calling MCP tool: $name")
        Log.d(TAG, "   Params: $params")
        
        val result = client.callTool(name, params)
        
        Log.d(TAG, "✅ Tool result: ${result.javaClass.simpleName}")
        result
    }
    
    suspend fun listTools(): List<McpTool> = withContext(Dispatchers.IO) {
        client.listTools()
    }
    
    fun getConnectionStatus(): McpConnectionStatus {
        return when {
            isConnected -> McpConnectionStatus.CONNECTED
            else -> McpConnectionStatus.DISCONNECTED
        }
    }
    
    companion object {
        private const val TAG = "McpRepository"
    }
}
