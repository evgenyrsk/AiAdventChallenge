package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams

/**
 * Оркестратор MCP tools.
 * 
 * Отвечает за:
 * - Детекцию запросов к MCP tools
 * - Вызов соответствующих tools
 * - Подготовку контекста для LLM
 */
interface McpToolOrchestrator {
    /**
     * Детектирует и выполняет MCP tool, если применимо.
     * 
     * @param userInput Ввод пользователя
     * @return Result с дополнительным контекстом или null если tool не применим
     */
    suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult
}

sealed class ToolExecutionResult {
    data class Success(val context: String) : ToolExecutionResult()
    object NoToolFound : ToolExecutionResult()
    data class Error(val message: String) : ToolExecutionResult()
}
