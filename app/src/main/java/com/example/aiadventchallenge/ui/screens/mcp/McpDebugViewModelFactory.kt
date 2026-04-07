package com.example.aiadventchallenge.ui.screens.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.domain.usecase.mcp.GetMcpToolsUseCase

class McpDebugViewModelFactory(
    private val getMcpToolsUseCase: GetMcpToolsUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(McpDebugViewModel::class.java)) {
            return McpDebugViewModel(getMcpToolsUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
