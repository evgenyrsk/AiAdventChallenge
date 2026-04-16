package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.rag.rewrite.RewriteResult

enum class RagAnswerPolicy {
    STRICT,
    RELAXED
}

enum class RagPostProcessingMode {
    NONE,
    THRESHOLD_ONLY,
    HEURISTIC_RERANK,
    THRESHOLD_PLUS_RERANK
}

data class RagPipelineConfig(
    val source: String,
    val strategy: String,
    val rewriteEnabled: Boolean,
    val postProcessingEnabled: Boolean,
    val postProcessingMode: RagPostProcessingMode,
    val retrievalTopKBeforeFilter: Int,
    val retrievalTopKAfterFilter: Int,
    val similarityThreshold: Double?,
    val maxChars: Int,
    val perDocumentLimit: Int,
    val fallbackOnEmptyPostProcessing: Boolean
)

data class RagContextChunk(
    val chunkId: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val score: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val rerankScore: Double? = null,
    val text: String,
    val filteredOut: Boolean = false,
    val filterReason: String? = null,
    val explanation: String? = null
)

data class RagRetrievalRequest(
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val rewriteResult: RewriteResult? = null,
    val config: RagPipelineConfig
)

data class RagRetrievalDebug(
    val topKBeforeFilter: Int,
    val finalTopK: Int,
    val similarityThreshold: Double? = null,
    val postProcessingMode: RagPostProcessingMode,
    val rewriteApplied: Boolean = false,
    val detectedIntent: String? = null,
    val rewriteStrategy: String? = null,
    val addedTerms: List<String> = emptyList(),
    val removedPhrases: List<String> = emptyList(),
    val fallbackApplied: Boolean,
    val fallbackReason: String? = null
)

data class RagRetrievalResult(
    val query: String,
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val effectiveQuery: String,
    val source: String,
    val strategy: String,
    val selectedCount: Int,
    val totalChars: Int,
    val contextText: String,
    val chunks: List<RagContextChunk>,
    val initialCandidates: List<RagContextChunk> = emptyList(),
    val finalCandidates: List<RagContextChunk> = emptyList(),
    val filteredCandidates: List<RagContextChunk> = emptyList(),
    val debug: RagRetrievalDebug,
    val contextEnvelope: String
)

data class PreparedRagRequest(
    val systemPromptSuffix: String,
    val userPrompt: String,
    val retrievalSummary: RetrievalSummary
)
