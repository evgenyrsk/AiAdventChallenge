package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.mcp.RetrievalSourceCard
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagRetrievalResult

/**
 * Builds the augmented prompt for RAG mode without coupling retrieval to UI or transport.
 */
class RagPromptBuilder {

    fun build(
        question: String,
        retrieval: RagRetrievalResult,
        policy: RagAnswerPolicy = RagAnswerPolicy.STRICT
    ): PreparedRagRequest {
        val policyInstruction = when (policy) {
            RagAnswerPolicy.STRICT -> {
                "Отвечай только на основе retrieved context. Если контекста недостаточно, прямо скажи об этом и не додумывай факты."
            }
            RagAnswerPolicy.RELAXED -> {
                "Используй retrieved context как основную базу ответа. Если данных мало, явно обозначь, какие выводы опираются на контекст, а какие являются общими рекомендациями."
            }
        }

        val systemPromptSuffix = buildString {
            appendLine()
            appendLine("RAG MODE")
            appendLine(policyInstruction)
            appendLine("По возможности указывай названия документов и секций, на которые опираешься.")
        }.trim()

        val userPrompt = buildString {
            appendLine("Вопрос пользователя:")
            appendLine(question)
            retrieval.rewrittenQuery?.let {
                appendLine()
                appendLine("Rewritten retrieval query:")
                appendLine(it)
            }
            appendLine()
            appendLine("Retrieved Context:")
            if (retrieval.contextText.isBlank()) {
                appendLine("Контекст не найден. Используй только то, что можно честно сказать без базы знаний, и явно обозначь нехватку релевантного контекста.")
            } else {
                appendLine(retrieval.contextText)
            }
        }.trim()

        return PreparedRagRequest(
            systemPromptSuffix = systemPromptSuffix,
            userPrompt = userPrompt,
            retrievalSummary = RetrievalSummary(
                query = retrieval.query.ifBlank { retrieval.effectiveQuery.ifBlank { question } },
                originalQuery = retrieval.originalQuery.ifBlank { question },
                rewrittenQuery = retrieval.rewrittenQuery,
                effectiveQuery = retrieval.effectiveQuery.ifBlank { question },
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
                chunks = retrieval.finalCandidates.map { chunk ->
                    RetrievalSourceCard(
                        chunkId = chunk.chunkId,
                        title = chunk.title,
                        relativePath = chunk.relativePath,
                        section = chunk.section,
                        finalRank = chunk.finalRank,
                        score = chunk.score,
                        semanticScore = chunk.semanticScore,
                        keywordScore = chunk.keywordScore,
                        rerankScore = chunk.rerankScore,
                        filteredOut = chunk.filteredOut,
                        filterReason = chunk.filterReason,
                        explanation = chunk.explanation
                    )
                },
                initialCandidates = retrieval.initialCandidates.map { chunk ->
                    RetrievalSourceCard(
                        chunkId = chunk.chunkId,
                        title = chunk.title,
                        relativePath = chunk.relativePath,
                        section = chunk.section,
                        finalRank = chunk.finalRank,
                        score = chunk.score,
                        semanticScore = chunk.semanticScore,
                        keywordScore = chunk.keywordScore,
                        rerankScore = chunk.rerankScore,
                        filteredOut = chunk.filteredOut,
                        filterReason = chunk.filterReason,
                        explanation = chunk.explanation
                    )
                },
                filteredCandidates = retrieval.filteredCandidates.map { chunk ->
                    RetrievalSourceCard(
                        chunkId = chunk.chunkId,
                        title = chunk.title,
                        relativePath = chunk.relativePath,
                        section = chunk.section,
                        finalRank = chunk.finalRank,
                        score = chunk.score,
                        semanticScore = chunk.semanticScore,
                        keywordScore = chunk.keywordScore,
                        rerankScore = chunk.rerankScore,
                        filteredOut = chunk.filteredOut,
                        filterReason = chunk.filterReason,
                        explanation = chunk.explanation
                    )
                }
            )
        )
    }
}
