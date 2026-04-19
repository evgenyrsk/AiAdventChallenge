package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagPipelineConfig
import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import com.example.aiadventchallenge.domain.rag.RagPromptBuilder
import com.example.aiadventchallenge.domain.rag.RagRetriever
import com.example.aiadventchallenge.rag.memory.RagConversationContext
import com.example.aiadventchallenge.rag.memory.TaskMemoryRagSupport

class PrepareRagRequestUseCase(
    private val ragRetriever: RagRetriever,
    private val ragPromptBuilder: RagPromptBuilder,
    private val rewriteQueryUseCase: RewriteQueryUseCase
) {
    suspend operator fun invoke(
        question: String,
        config: RagPipelineConfig = FitnessRagConfig.basicPipeline,
        policy: RagAnswerPolicy = RagAnswerPolicy.STRICT,
        conversationContext: RagConversationContext? = null
    ): PreparedRagRequest {
        val retrievalHints = TaskMemoryRagSupport.retrievalHints(conversationContext)
        val rewriteSeed = TaskMemoryRagSupport.buildRewriteSeed(question, retrievalHints)

        val rewriteResult = rewriteSeed
            .takeIf { config.rewriteEnabled }
            ?.let(rewriteQueryUseCase::invoke)

        val rewrittenQuery = TaskMemoryRagSupport.normalizeRewrittenQuery(
            question = question,
            rewriteSeed = rewriteSeed,
            rewrittenQuery = rewriteResult?.rewrittenQuery
        )

        val effectiveQuery = TaskMemoryRagSupport.buildEffectiveQuery(
            question = question,
            rewrittenQuery = rewrittenQuery,
            retrievalHints = retrievalHints
        )

        val retrieval = ragRetriever.retrieve(
            RagRetrievalRequest(
                originalQuery = question,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = effectiveQuery,
                rewriteResult = rewriteResult,
                config = config,
                conversationGoal = conversationContext?.taskState?.dialogGoal,
                retrievalHints = retrievalHints,
                memorySummary = conversationContext?.taskState?.latestSummary
            )
        )

        Log.d(
            TAG,
            "Prepared RAG request: source=${config.source} strategy=${config.strategy} selectedChunks=${retrieval.selectedCount} rewritten=${rewrittenQuery != null}"
        )

        return ragPromptBuilder.build(
            question = question,
            retrieval = retrieval,
            policy = policy,
            conversationContext = conversationContext
        )
    }

    companion object {
        private const val TAG = "PrepareRagRequest"
    }
}
