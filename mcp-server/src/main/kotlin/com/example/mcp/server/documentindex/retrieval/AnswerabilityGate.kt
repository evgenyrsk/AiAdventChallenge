package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.GateDecision
import com.example.mcp.server.documentindex.model.RetrievalConfidenceLevel
import com.example.mcp.server.documentindex.model.RetrievalConfidenceSummary
import com.example.mcp.server.documentindex.model.RetrievalContextInput
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksResult

class AnswerabilityGate {

    fun evaluate(
        retrieval: RetrieveRelevantChunksResult,
        config: RetrievalPipelineConfig,
        contextInput: RetrievalContextInput = RetrievalContextInput(userQuestion = retrieval.originalQuery)
    ): RetrievalConfidenceSummary {
        val finalCandidates = retrieval.finalCandidates
        val top = finalCandidates.firstOrNull()
        val finalChunkCount = finalCandidates.size
        val rerankThreshold = config.rerankScoreThreshold ?: config.similarityThreshold
        val coverageScore = coverageScore(contextInput.userQuestion, finalCandidates)
        val consistencyScore = consistencyScore(finalCandidates)
        val evidenceScore = retrieval.evidenceSpans.maxOfOrNull { it.score }
            ?: top?.rerankScore
            ?: top?.fusionScore
            ?: 0.0
        val offTopic = coverageScore < config.minimumCoverageScore && finalChunkCount > 0

        val reason = when {
            finalChunkCount < config.minAnswerableChunks -> "no_relevant_chunks"
            config.similarityThreshold != null && (top?.semanticScore ?: 0.0) < config.similarityThreshold -> "low_relevance"
            rerankThreshold != null && top?.rerankScore != null && top.rerankScore < rerankThreshold -> "below_threshold_after_rerank"
            offTopic -> "off_topic_retrieval"
            evidenceScore < config.minimumEvidenceScore -> "weak_evidence"
            retrieval.debug.fallbackApplied && !config.allowAnswerWithRetrievalFallback ->
                retrieval.debug.fallbackReason ?: "retrieval_fallback_not_allowed"
            else -> null
        }

        val level = when {
            reason == null -> RetrievalConfidenceLevel.ANSWERABLE_GROUNDED
            offTopic -> RetrievalConfidenceLevel.OFF_TOPIC_RETRIEVAL
            finalChunkCount > 0 && coverageScore >= config.minimumCoverageScore * 0.75 ->
                RetrievalConfidenceLevel.PARTIALLY_ANSWERABLE
            else -> RetrievalConfidenceLevel.INSUFFICIENT_EVIDENCE
        }

        return RetrievalConfidenceSummary(
            answerable = level == RetrievalConfidenceLevel.ANSWERABLE_GROUNDED,
            reason = reason,
            minAnswerableChunks = config.minAnswerableChunks,
            finalChunkCount = finalChunkCount,
            topSimilarityScore = top?.score,
            topSemanticScore = top?.semanticScore,
            topRerankScore = top?.rerankScore,
            similarityThreshold = config.similarityThreshold,
            rerankThreshold = rerankThreshold,
            retrievalFallbackApplied = retrieval.debug.fallbackApplied,
            confidenceLevel = level,
            coverageScore = coverageScore,
            consistencyScore = consistencyScore,
            evidenceScore = evidenceScore
        )
    }

    fun buildDecision(
        retrieval: RetrieveRelevantChunksResult,
        confidence: RetrievalConfidenceSummary,
        contextInput: RetrievalContextInput = RetrievalContextInput(userQuestion = retrieval.originalQuery)
    ): GateDecision {
        return GateDecision(
            confidenceLevel = confidence.confidenceLevel,
            reason = confidence.reason,
            coverageScore = confidence.coverageScore,
            consistencyScore = confidence.consistencyScore,
            evidenceScore = confidence.evidenceScore,
            offTopic = confidence.confidenceLevel == RetrievalConfidenceLevel.OFF_TOPIC_RETRIEVAL &&
                coverageScore(contextInput.userQuestion, retrieval.finalCandidates) < 0.2
        )
    }

    private fun coverageScore(question: String, finalCandidates: List<com.example.mcp.server.documentindex.model.RetrievedContextChunk>): Double {
        val queryTerms = tokenize(question)
        if (finalCandidates.isEmpty()) return 0.0
        if (queryTerms.isEmpty()) return 1.0
        val evidenceTerms = finalCandidates
            .flatMap { tokenize("${it.title} ${it.section} ${it.fullText.ifBlank { it.excerpt }}") }
            .toSet()
        return queryTerms.count(evidenceTerms::contains).toDouble() / queryTerms.size.toDouble()
    }

    private fun consistencyScore(finalCandidates: List<com.example.mcp.server.documentindex.model.RetrievedContextChunk>): Double {
        if (finalCandidates.size <= 1) return if (finalCandidates.isEmpty()) 0.0 else 1.0
        val sections = finalCandidates.map { "${it.relativePath}#${it.section}" }.distinct().size
        return 1.0 / sections.toDouble().coerceAtLeast(1.0)
    }

    private fun tokenize(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}_]+"))
        .filter { it.length >= 3 }
        .toSet()
}
