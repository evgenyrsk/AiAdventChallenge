package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.AnswerWithRetrievalRequest
import com.example.mcp.server.documentindex.model.AnswerWithRetrievalResult
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksRequest

class RetrievalOrchestrationService(
    private val retrievalService: DocumentRetrievalService = DocumentRetrievalService()
) {

    fun answerWithRetrieval(request: AnswerWithRetrievalRequest): AnswerWithRetrievalResult {
        val retrieval = retrievalService.retrieveRelevantChunks(
            RetrieveRelevantChunksRequest(
                query = request.query,
                originalQuery = request.originalQuery,
                rewrittenQuery = request.rewrittenQuery,
                effectiveQuery = request.effectiveQuery,
                source = request.source,
                strategy = request.strategy,
                topK = request.topK,
                maxChars = request.maxChars,
                documentType = request.documentType,
                relativePathContains = request.relativePathContains,
                perDocumentLimit = request.perDocumentLimit,
                rewriteDebug = request.rewriteDebug,
                pipelineConfig = request.pipelineConfig
            )
        )

        val systemPrompt = buildString {
            appendLine("You are a helpful assistant answering with retrieved project knowledge.")
            appendLine("Answer only from the provided retrieved context.")
            appendLine("Do not invent facts, sources, sections, or quotes.")
            appendLine("If the context is insufficient, say so explicitly and ask the user to clarify.")
            appendLine("The application will attach sources and quotes separately, so focus on answerText only.")
        }.trim()

        val userPrompt = buildString {
            appendLine("User question:")
            appendLine(request.originalQuery)
            appendLine()
            appendLine("Retrieved context:")
            append(retrieval.contextEnvelope)
        }.trim()

        val answerPrompt = buildString {
            appendLine(systemPrompt)
            appendLine()
            append(userPrompt)
        }.trim()

        val fallbackAnswer = retrieval.grounding
            ?.takeIf { it.isFallbackIDontKnow }
            ?.let { "Не знаю на основе найденного контекста. Уточни вопрос или сформулируй его иначе." }

        return AnswerWithRetrievalResult(
            query = request.effectiveQuery,
            retrieval = retrieval,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            answerPrompt = answerPrompt,
            retrievalApplied = retrieval.selectedCount > 0,
            fallbackAnswer = fallbackAnswer
        )
    }
}
