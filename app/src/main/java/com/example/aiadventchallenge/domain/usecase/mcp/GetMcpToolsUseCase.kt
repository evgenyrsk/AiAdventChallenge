package com.example.aiadventchallenge.domain.usecase.mcp

import com.example.aiadventchallenge.data.mcp.McpRepository
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult

class GetMcpToolsUseCase(
    private val mcpRepository: McpRepository
) {
    suspend operator fun invoke(): McpConnectionResult {
        return mcpRepository.connectAndListTools()
    }
}
