package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    
    suspend fun executeMultiServerFlow(
        prompt: String
    ): MultiServerFlowResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎯 Executing multi-server flow for: $prompt")
            
            // Call the new execute_multi_server_flow tool
            val params = mapOf(
                "prompt" to prompt
            )
            
            val result = client.callTool("execute_multi_server_flow", params)
            
            Log.d(TAG, "✅ Multi-server flow completed")
            Log.d(TAG, "   Result: $result")
            
            (result as? McpToolData.MultiServerFlow)?.result ?: MultiServerFlowResult(
                success = false,
                flowName = "unknown",
                flowId = "",
                stepsExecuted = 0,
                totalSteps = 0,
                finalResult = null,
                executionSteps = emptyList(),
                durationMs = 0,
                errorMessage = "Invalid result format"
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Multi-server flow failed", e)
            
            MultiServerFlowResult(
                success = false,
                flowName = "unknown",
                flowId = "",
                stepsExecuted = 0,
                totalSteps = 0,
                finalResult = null,
                executionSteps = emptyList(),
                durationMs = 0,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    suspend fun executeFitnessSummaryToReminderFlow(): MultiServerFlowResult {
        val prompt = "Найди последние фитнес логи за неделю, составь из них краткое описание и создай напоминание на завтра утром"
        return executeMultiServerFlow(prompt)
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
