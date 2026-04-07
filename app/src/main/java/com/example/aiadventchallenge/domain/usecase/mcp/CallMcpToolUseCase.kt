package com.example.aiadventchallenge.domain.usecase.mcp

import com.example.aiadventchallenge.data.mcp.McpRepository

class CallMcpToolUseCase(
    private val mcpRepository: McpRepository
) {
    suspend operator fun invoke(
        name: String,
        params: Map<String, Any?>
    ): String {
        return mcpRepository.callTool(name, params)
    }
}
