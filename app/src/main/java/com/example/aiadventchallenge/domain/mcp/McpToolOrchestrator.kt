package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.model.GroundedAnswerPayload
import com.example.aiadventchallenge.domain.model.GroundedQuote
import com.example.aiadventchallenge.domain.model.GroundedSource
import com.example.aiadventchallenge.domain.model.RagConfidenceSummary
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
    suspend fun detectAndExecuteTool(
        userInput: String,
        allowKnowledgeRetrieval: Boolean = true
    ): ToolExecutionResult
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
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val source: String,
    val strategy: String,
    val selectedCount: Int,
    val topKBeforeFilter: Int,
    val finalTopK: Int,
    val similarityThreshold: Double? = null,
    val postProcessingMode: String = "NONE",
    val rewriteApplied: Boolean = false,
    val detectedIntent: String? = null,
    val rewriteStrategy: String? = null,
    val addedTerms: List<String> = emptyList(),
    val removedPhrases: List<String> = emptyList(),
    val rerankProvider: String? = null,
    val rerankModel: String? = null,
    val rerankApplied: Boolean = false,
    val rerankInputCount: Int = 0,
    val rerankOutputCount: Int = 0,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long? = null,
    val rerankFallbackUsed: Boolean = false,
    val rerankFallbackReason: String? = null,
    val fallbackApplied: Boolean = false,
    val fallbackReason: String? = null,
    val contextEnvelope: String,
    val chunks: List<RetrievalSourceCard>,
    val initialCandidates: List<RetrievalSourceCard> = emptyList(),
    val filteredCandidates: List<RetrievalSourceCard> = emptyList(),
    val groundedAnswer: GroundedAnswerPayload? = null
)

data class RetrievalSourceCard(
    val chunkId: String = "",
    val source: String = "",
    val title: String,
    val relativePath: String,
    val section: String,
    val finalRank: Int? = null,
    val score: Double,
    val semanticScore: Double = 0.0,
    val keywordScore: Double = 0.0,
    val rerankScore: Double? = null,
    val fullText: String? = null,
    val filteredOut: Boolean = false,
    val filterReason: String? = null,
    val explanation: String? = null
)

data class RetrievalGroundingCard(
    val sources: List<GroundedSource> = emptyList(),
    val quotes: List<GroundedQuote> = emptyList(),
    val confidence: RagConfidenceSummary,
    val fallbackReason: String? = null,
    val isFallbackIDontKnow: Boolean = false
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
