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
    THRESHOLD_PLUS_RERANK,
    MODEL_RERANK,
    THRESHOLD_PLUS_MODEL_RERANK
}

enum class RagRerankFallbackPolicy {
    HEURISTIC_THEN_RETRIEVAL,
    RETRIEVAL_ONLY
}

enum class RagAnswerMode {
    GROUNDED,
    FALLBACK_I_DONT_KNOW
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
    val minAnswerableChunks: Int,
    val allowAnswerWithRetrievalFallback: Boolean,
    val maxChars: Int,
    val perDocumentLimit: Int,
    val fallbackOnEmptyPostProcessing: Boolean,
    val rerankEnabled: Boolean = false,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long = 3500,
    val rerankFallbackPolicy: RagRerankFallbackPolicy = RagRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL,
    val queryContext: String? = null
)

data class RagContextChunk(
    val chunkId: String,
    val source: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val finalRank: Int? = null,
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
    val rerankProvider: String? = null,
    val rerankModel: String? = null,
    val rerankApplied: Boolean = false,
    val rerankInputCount: Int = 0,
    val rerankOutputCount: Int = 0,
    val rerankScoreThreshold: Double? = null,
    val rerankTimeoutMs: Long? = null,
    val rerankFallbackUsed: Boolean = false,
    val rerankFallbackReason: String? = null,
    val fallbackApplied: Boolean,
    val fallbackReason: String? = null
)

data class GroundedSource(
    val source: String? = null,
    val title: String? = null,
    val section: String? = null,
    val chunkId: String? = null,
    val similarityScore: Double? = null,
    val rerankScore: Double? = null,
    val finalRank: Int? = null,
    val relativePath: String? = null
)

data class GroundedQuote(
    val quotedText: String,
    val source: String? = null,
    val title: String? = null,
    val section: String? = null,
    val chunkId: String? = null,
    val relativePath: String? = null,
    val quoteRank: Int? = null,
    val originFinalRank: Int? = null
)

data class RagConfidenceSummary(
    val answerable: Boolean,
    val reason: String? = null,
    val minAnswerableChunks: Int,
    val finalChunkCount: Int,
    val topSimilarityScore: Double? = null,
    val topSemanticScore: Double? = null,
    val topRerankScore: Double? = null,
    val similarityThreshold: Double? = null,
    val rerankThreshold: Double? = null,
    val retrievalFallbackApplied: Boolean = false
)

data class RagGrounding(
    val sources: List<GroundedSource> = emptyList(),
    val quotes: List<GroundedQuote> = emptyList(),
    val confidence: RagConfidenceSummary,
    val fallbackReason: String? = null,
    val isFallbackIDontKnow: Boolean = false
)

data class GroundedAnswerPayload(
    val answerText: String,
    val sources: List<GroundedSource> = emptyList(),
    val quotes: List<GroundedQuote> = emptyList(),
    val answerMode: RagAnswerMode,
    val pipelineMode: RagPostProcessingMode,
    val confidence: RagConfidenceSummary,
    val fallbackReason: String? = null,
    val isFallbackIDontKnow: Boolean = false
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
    val contextEnvelope: String,
    val grounding: RagGrounding? = null
)

data class PreparedRagRequest(
    val systemPromptSuffix: String,
    val userPrompt: String,
    val retrievalSummary: RetrievalSummary,
    val fallbackAnswerText: String? = null
)
