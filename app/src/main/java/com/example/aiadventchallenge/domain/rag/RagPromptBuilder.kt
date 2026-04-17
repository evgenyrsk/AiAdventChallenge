package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.mcp.RetrievalSourceCard
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.GroundedAnswerPayload
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RagAnswerMode
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
            appendLine("Отвечай только по найденному контексту.")
            appendLine("Не придумывай источники, секции, цитаты или факты вне retrieved context.")
            appendLine("Приложение само прикрепит sources и quotes детерминированно, поэтому сгенерируй только answerText.")
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

        val fallbackAnswerText = retrieval.grounding
            ?.takeIf { it.isFallbackIDontKnow }
            ?.let { "Не знаю на основе найденного контекста. Уточни вопрос или сформулируй его иначе." }

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
                chunks = retrieval.finalCandidates.map(::toSourceCard),
                initialCandidates = retrieval.initialCandidates.map(::toSourceCard),
                filteredCandidates = retrieval.filteredCandidates.map(::toSourceCard),
                groundedAnswer = retrieval.grounding?.let { grounding ->
                    GroundedAnswerPayload(
                        answerText = fallbackAnswerText.orEmpty(),
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
            ),
            fallbackAnswerText = fallbackAnswerText
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
