package com.example.aiadventchallenge.data.mcp

import com.example.aiadventchallenge.domain.model.RagPipelineConfig
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRerankFallbackPolicy

object FitnessRagConfig {
    const val DEFAULT_SOURCE = "fitness_knowledge"
    const val DEFAULT_STRATEGY = "structure_aware"
    const val DEFAULT_TOP_K = 4
    const val DEFAULT_MAX_CHARS = 2500
    const val DEFAULT_PER_DOCUMENT_LIMIT = 1
    const val ENHANCED_SIMILARITY_THRESHOLD = 0.2
    const val ENHANCED_TOP_K_BEFORE_FILTER = 6
    const val ENHANCED_TOP_K_AFTER_FILTER = 4
    const val ENHANCED_RERANK_TIMEOUT_MS = 3500L

    val basicPipeline: RagPipelineConfig
        get() = RagPipelineConfig(
            source = DEFAULT_SOURCE,
            strategy = DEFAULT_STRATEGY,
            rewriteEnabled = false,
            postProcessingEnabled = false,
            postProcessingMode = RagPostProcessingMode.NONE,
            retrievalTopKBeforeFilter = DEFAULT_TOP_K,
            retrievalTopKAfterFilter = DEFAULT_TOP_K,
            similarityThreshold = null,
            maxChars = DEFAULT_MAX_CHARS,
            perDocumentLimit = DEFAULT_PER_DOCUMENT_LIMIT,
            fallbackOnEmptyPostProcessing = true,
            rerankEnabled = false,
            rerankScoreThreshold = null,
            rerankTimeoutMs = ENHANCED_RERANK_TIMEOUT_MS,
            rerankFallbackPolicy = RagRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL,
            queryContext = null
        )

    val enhancedPipeline: RagPipelineConfig
        get() = RagPipelineConfig(
            source = DEFAULT_SOURCE,
            strategy = DEFAULT_STRATEGY,
            rewriteEnabled = true,
            postProcessingEnabled = true,
            postProcessingMode = RagPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
            retrievalTopKBeforeFilter = ENHANCED_TOP_K_BEFORE_FILTER,
            retrievalTopKAfterFilter = ENHANCED_TOP_K_AFTER_FILTER,
            similarityThreshold = ENHANCED_SIMILARITY_THRESHOLD,
            maxChars = DEFAULT_MAX_CHARS,
            perDocumentLimit = DEFAULT_PER_DOCUMENT_LIMIT,
            fallbackOnEmptyPostProcessing = true,
            rerankEnabled = true,
            rerankScoreThreshold = ENHANCED_SIMILARITY_THRESHOLD,
            rerankTimeoutMs = ENHANCED_RERANK_TIMEOUT_MS,
            rerankFallbackPolicy = RagRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL,
            queryContext = null
        )
}
