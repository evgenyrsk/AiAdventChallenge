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
            appendLine("Use the provided retrieval context when it is relevant.")
            appendLine("If the context is insufficient, say so explicitly and avoid inventing details.")
            appendLine("Prefer referencing file names and sections when making concrete claims.")
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

        return AnswerWithRetrievalResult(
            query = request.effectiveQuery,
            retrieval = retrieval,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            answerPrompt = answerPrompt,
            retrievalApplied = retrieval.selectedCount > 0
        )
    }
}
