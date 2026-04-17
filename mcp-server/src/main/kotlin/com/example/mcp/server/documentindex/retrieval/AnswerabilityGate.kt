package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalConfidenceSummary
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksResult

class AnswerabilityGate {

    fun evaluate(
        retrieval: RetrieveRelevantChunksResult,
        config: RetrievalPipelineConfig
    ): RetrievalConfidenceSummary {
        val finalCandidates = retrieval.finalCandidates
        val top = finalCandidates.firstOrNull()
        val finalChunkCount = finalCandidates.size
        val rerankThreshold = config.rerankScoreThreshold ?: config.similarityThreshold
        val reason = when {
            finalChunkCount < config.minAnswerableChunks -> "no_relevant_chunks"
            config.similarityThreshold != null &&
                (top?.semanticScore ?: 0.0) < config.similarityThreshold -> "low_relevance"
            rerankThreshold != null &&
                top?.rerankScore != null &&
                top.rerankScore < rerankThreshold -> "below_threshold_after_rerank"
            retrieval.debug.fallbackApplied && !config.allowAnswerWithRetrievalFallback ->
                retrieval.debug.fallbackReason ?: "retrieval_fallback_not_allowed"
            else -> null
        }

        return RetrievalConfidenceSummary(
            answerable = reason == null,
            reason = reason,
            minAnswerableChunks = config.minAnswerableChunks,
            finalChunkCount = finalChunkCount,
            topSimilarityScore = top?.score,
            topSemanticScore = top?.semanticScore,
            topRerankScore = top?.rerankScore,
            similarityThreshold = config.similarityThreshold,
            rerankThreshold = rerankThreshold,
            retrievalFallbackApplied = retrieval.debug.fallbackApplied
        )
    }
}
