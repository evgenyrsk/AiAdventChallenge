package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.mcp.RetrievalSourceCard
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.GroundedAnswerPayload
import com.example.aiadventchallenge.domain.model.RagAnswerMode
import com.example.aiadventchallenge.domain.model.RagRetrievalResult

class RetrievalSummaryFactory {
    fun create(
        retrieval: RagRetrievalResult,
        fallbackAnswerText: String = ""
    ): RetrievalSummary {
        return RetrievalSummary(
            query = retrieval.query.ifBlank { retrieval.effectiveQuery.ifBlank { retrieval.originalQuery } },
            originalQuery = retrieval.originalQuery,
            rewrittenQuery = retrieval.rewrittenQuery,
            effectiveQuery = retrieval.effectiveQuery,
            source = retrieval.source,
            strategy = retrieval.strategy,
            selectedCount = retrieval.selectedCount,
            topKBeforeFilter = retrieval.debug.topKBeforeFilter,
            finalTopK = retrieval.debug.finalTopK,
            similarityThreshold = retrieval.debug.similarityThreshold,
            postProcessingMode = retrieval.debug.postProcessingMode.name,
            rewriteApplied = retrieval.debug.rewriteApplied,
            detectedIntent = retrieval.debug.detectedIntent,
            rewriteStrategy = retrieval.debug.rewriteStrategy,
            addedTerms = retrieval.debug.addedTerms,
            removedPhrases = retrieval.debug.removedPhrases,
            rerankProvider = retrieval.debug.rerankProvider,
            rerankModel = retrieval.debug.rerankModel,
            rerankApplied = retrieval.debug.rerankApplied,
            rerankInputCount = retrieval.debug.rerankInputCount,
            rerankOutputCount = retrieval.debug.rerankOutputCount,
            rerankScoreThreshold = retrieval.debug.rerankScoreThreshold,
            rerankTimeoutMs = retrieval.debug.rerankTimeoutMs,
            rerankFallbackUsed = retrieval.debug.rerankFallbackUsed,
            rerankFallbackReason = retrieval.debug.rerankFallbackReason,
            fallbackApplied = retrieval.debug.fallbackApplied,
            fallbackReason = retrieval.debug.fallbackReason,
            contextEnvelope = retrieval.contextEnvelope,
            chunks = retrieval.finalCandidates.map(::toSourceCard),
            initialCandidates = retrieval.initialCandidates.map(::toSourceCard),
            filteredCandidates = retrieval.filteredCandidates.map(::toSourceCard),
            groundedAnswer = retrieval.grounding?.let { grounding ->
                GroundedAnswerPayload(
                    answerText = fallbackAnswerText,
                    sources = grounding.sources,
                    quotes = grounding.quotes,
                    answerMode = if (grounding.isFallbackIDontKnow) {
                        RagAnswerMode.FALLBACK_I_DONT_KNOW
                    } else {
                        RagAnswerMode.GROUNDED
                    },
                    pipelineMode = retrieval.debug.postProcessingMode,
                    confidence = grounding.confidence,
                    fallbackReason = grounding.fallbackReason,
                    isFallbackIDontKnow = grounding.isFallbackIDontKnow
                )
            }
        )
    }

    private fun toSourceCard(chunk: com.example.aiadventchallenge.domain.model.RagContextChunk): RetrievalSourceCard {
        return RetrievalSourceCard(
            chunkId = chunk.chunkId,
            source = chunk.source,
            title = chunk.title,
            relativePath = chunk.relativePath,
            section = chunk.section,
            finalRank = chunk.finalRank,
            score = chunk.score,
            semanticScore = chunk.semanticScore,
            keywordScore = chunk.keywordScore,
            rerankScore = chunk.rerankScore,
            fullText = chunk.text,
            filteredOut = chunk.filteredOut,
            filterReason = chunk.filterReason,
            explanation = chunk.explanation
        )
    }
}
