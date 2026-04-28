package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.mcp.DeveloperDocsRagConfig
import com.example.aiadventchallenge.domain.model.PromptProfile
import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.RagRetriever

class DeveloperDocsRetriever(
    private val prepareRagRequestUseCase: PrepareRagRequestUseCase,
    private val ragRetriever: RagRetriever
) {
    suspend fun retrieve(
        question: String,
        promptProfile: PromptProfile
    ): DeveloperDocsRetrievalResult {
        val prepared = prepareRagRequestUseCase(
            question = question,
            config = DeveloperDocsRagConfig.helpPipeline,
            policy = RagAnswerPolicy.STRICT,
            conversationContext = null,
            promptProfile = promptProfile
        )

        val retrieval = ragRetriever.retrieve(
            com.example.aiadventchallenge.domain.model.RagRetrievalRequest(
                originalQuery = question,
                effectiveQuery = prepared.retrievalSummary.effectiveQuery,
                rewrittenQuery = prepared.retrievalSummary.rewrittenQuery,
                config = DeveloperDocsRagConfig.helpPipeline
            )
        )
        return DeveloperDocsRetrievalResult(retrieval = retrieval, retrievalLatencyMs = prepared.retrievalLatencyMs)
    }
}

data class DeveloperDocsRetrievalResult(
    val retrieval: RagRetrievalResult,
    val retrievalLatencyMs: Long?
)
