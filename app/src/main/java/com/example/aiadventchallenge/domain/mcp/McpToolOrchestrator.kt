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
    data class Success(
        val context: String,
        val retrievalSummary: RetrievalSummary? = null
    ) : ToolExecutionResult()
    object NoToolFound : ToolExecutionResult()
    data class Error(val message: String) : ToolExecutionResult()
    data class MissingParameters(val missingParams: List<ParameterInfo>) : ToolExecutionResult()
}

data class RetrievalSummary(
    val query: String,
    val source: String,
    val strategy: String,
    val selectedCount: Int,
    val contextEnvelope: String,
    val chunks: List<RetrievalSourceCard>
)

data class RetrievalSourceCard(
    val title: String,
    val relativePath: String,
    val section: String,
    val score: Double
)

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val missingParams: List<ParameterInfo>) : ValidationResult()
}

data class ParameterInfo(
    val name: String,
    val description: String,
    val example: String
)

data class ToolExecutionStep(
    val tool: String,
    val serverId: String,
    val success: Boolean,
    val durationMs: Long,
    val result: Any?,
    val error: String?
)

data class ToolCall(
    val tool: String,
    val dependsOn: String? = null,
    val params: Map<String, Any?> = emptyMap()
)

data class FitnessIntent(
    val needsNutritionMetrics: Boolean,
    val needsMealGuidance: Boolean,
    val needsTrainingGuidance: Boolean,
    val needsKnowledgeRetrieval: Boolean = false,
    val originalQuery: String = "",
    val extractedParams: Map<String, Any?> = emptyMap()
)
