package com.example.aiadventchallenge.ui.screens.mcp

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import com.example.aiadventchallenge.domain.usecase.mcp.GetMcpToolsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class McpDebugViewModel(
    private val getMcpToolsUseCase: GetMcpToolsUseCase
) : ViewModel() {

    private val TAG = "McpDebugViewModel"

    private val _connectionStatus = MutableStateFlow<McpConnectionStatus>(McpConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<McpConnectionStatus> = _connectionStatus.asStateFlow()

    private val _tools = MutableStateFlow<List<McpTool>>(emptyList())
    val tools: StateFlow<List<McpTool>> = _tools.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        Log.d(TAG, "🎯 McpDebugViewModel created")
    }

    fun checkMcpConnection() {
        if (_isLoading.value) return

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _connectionStatus.value = McpConnectionStatus.CONNECTING
                _error.value = null

                Log.d(TAG, "🔍 Checking MCP connection...")

                val result = getMcpToolsUseCase()

                if (result.isConnected) {
                    Log.d(TAG, "✅ MCP connected successfully")
                    Log.d(TAG, "📦 Tools received: ${result.tools.size}")

                    _connectionStatus.value = McpConnectionStatus.CONNECTED
                    _tools.value = result.tools
                    _error.value = null
                } else {
                    Log.e(TAG, "❌ MCP connection failed: ${result.error}")

                    _connectionStatus.value = McpConnectionStatus.ERROR
                    _tools.value = emptyList()
                    _error.value = result.error
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ MCP check error", e)

                _connectionStatus.value = McpConnectionStatus.ERROR
                _tools.value = emptyList()
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
