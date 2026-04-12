package com.example.aiadventchallenge.domain.usecase.mcp

import com.example.aiadventchallenge.data.mcp.McpRepository
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult

class CallMcpToolUseCase(
    private val mcpRepository: McpRepository
) {
    suspend operator fun invoke(
        name: String,
        params: Map<String, Any?>
    ): McpToolData {
        return mcpRepository.callTool(name, params)
    }

    suspend fun executeMultiServerFlow(prompt: String): MultiServerFlowResult {
        return mcpRepository.executeMultiServerFlow(prompt)
    }
}
