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
            appendLine()
            appendLine("Retrieved Context:")
            if (retrieval.contextText.isBlank()) {
                appendLine("Контекст не найден.")
            } else {
                appendLine(retrieval.contextText)
            }
        }.trim()

        return PreparedRagRequest(
            systemPromptSuffix = systemPromptSuffix,
            userPrompt = userPrompt,
            retrievalSummary = RetrievalSummary(
                query = retrieval.query.ifBlank { question },
                source = retrieval.source,
                strategy = retrieval.strategy,
                selectedCount = retrieval.selectedCount,
                contextEnvelope = retrieval.contextEnvelope,
                chunks = retrieval.chunks.map { chunk ->
                    RetrievalSourceCard(
                        title = chunk.title,
                        relativePath = chunk.relativePath,
                        section = chunk.section,
                        score = chunk.score
                    )
                }
            )
        )
    }
}
