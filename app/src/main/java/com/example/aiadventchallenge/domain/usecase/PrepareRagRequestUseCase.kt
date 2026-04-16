package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagPipelineConfig
import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import com.example.aiadventchallenge.domain.rag.RagPromptBuilder
import com.example.aiadventchallenge.domain.rag.RagRetriever

class PrepareRagRequestUseCase(
    private val ragRetriever: RagRetriever,
    private val ragPromptBuilder: RagPromptBuilder,
    private val rewriteQueryUseCase: RewriteQueryUseCase
) {
    suspend operator fun invoke(
        question: String,
        config: RagPipelineConfig = FitnessRagConfig.basicPipeline,
        policy: RagAnswerPolicy = RagAnswerPolicy.STRICT
    ): PreparedRagRequest {
        val rewriteResult = question
            .takeIf { config.rewriteEnabled }
            ?.let(rewriteQueryUseCase::invoke)

        val rewrittenQuery = rewriteResult
            ?.rewrittenQuery
            ?.takeUnless { it.equals(question, ignoreCase = true) }

        val retrieval = ragRetriever.retrieve(
            RagRetrievalRequest(
                originalQuery = question,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = rewrittenQuery ?: question,
                rewriteResult = rewriteResult,
                config = config
            )
        )

        Log.d(
            TAG,
            "Prepared RAG request: source=${config.source} strategy=${config.strategy} selectedChunks=${retrieval.selectedCount} rewritten=${rewrittenQuery != null}"
        )

        return ragPromptBuilder.build(
            question = question,
            retrieval = retrieval,
            policy = policy
        )
    }

    companion object {
        private const val TAG = "PrepareRagRequest"
    }
}
