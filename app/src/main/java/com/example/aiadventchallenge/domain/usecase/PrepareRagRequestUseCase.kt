package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.rag.RagPromptBuilder
import com.example.aiadventchallenge.domain.rag.RagRetriever

class PrepareRagRequestUseCase(
    private val ragRetriever: RagRetriever,
    private val ragPromptBuilder: RagPromptBuilder
) {
    suspend operator fun invoke(
        question: String,
        source: String = FitnessRagConfig.DEFAULT_SOURCE,
        strategy: String = FitnessRagConfig.DEFAULT_STRATEGY,
        topK: Int = FitnessRagConfig.DEFAULT_TOP_K,
        maxChars: Int = FitnessRagConfig.DEFAULT_MAX_CHARS,
        perDocumentLimit: Int = FitnessRagConfig.DEFAULT_PER_DOCUMENT_LIMIT,
        policy: RagAnswerPolicy = RagAnswerPolicy.STRICT
    ): PreparedRagRequest {
        val retrieval = ragRetriever.retrieve(
            query = question,
            source = source,
            strategy = strategy,
            topK = topK,
            maxChars = maxChars,
            perDocumentLimit = perDocumentLimit
        )

        Log.d(
            TAG,
            "Prepared RAG request: source=$source strategy=$strategy selectedChunks=${retrieval.selectedCount}"
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
