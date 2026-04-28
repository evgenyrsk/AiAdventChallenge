package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.mcp.RetrievalSourceCard
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.GroundedAnswerPayload
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.PromptProfile
import com.example.aiadventchallenge.domain.model.RagAnswerMode
import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.rag.memory.RagConversationContext
import com.example.aiadventchallenge.rag.memory.TaskMemoryRagSupport

/**
 * Builds the augmented prompt for RAG mode without coupling retrieval to UI or transport.
 */
class RagPromptBuilder {
    private val retrievalSummaryFactory = RetrievalSummaryFactory()

    fun build(
        question: String,
        retrieval: RagRetrievalResult,
        policy: RagAnswerPolicy = RagAnswerPolicy.STRICT,
        conversationContext: RagConversationContext? = null,
        promptProfile: PromptProfile = PromptProfile.BASELINE
    ): PreparedRagRequest {
        val conversationTaskBlock = TaskMemoryRagSupport.buildPromptBlock(conversationContext)

        val policyInstruction = when (policy) {
            RagAnswerPolicy.STRICT -> {
                "Отвечай только на основе retrieved context. Если контекста недостаточно, прямо скажи об этом и не додумывай факты."
            }
            RagAnswerPolicy.RELAXED -> {
                "Используй retrieved context как основную базу ответа. Если данных мало, явно обозначь, какие выводы опираются на контекст, а какие являются общими рекомендациями."
            }
        }

        val profileInstructions = when (promptProfile) {
            PromptProfile.BASELINE -> listOf(
                "Отвечай только по найденному контексту.",
                "Не придумывай источники, секции, цитаты или факты вне retrieved context.",
                "Task memory помогает понять цель и ограничения диалога, но не является доказательной базой.",
                "Приложение само прикрепит sources и quotes детерминированно, поэтому сгенерируй только answerText."
            )
            PromptProfile.OPTIMIZED_CHAT,
            PromptProfile.OPTIMIZED_RAG -> listOf(
                "Используй только retrieved context как доказательную базу ответа.",
                "Если данных недостаточно, честно скажи об этом одним явным предложением.",
                "Не выдумывай факты, источники, цитаты, дозировки, цифры или рекомендации вне retrieved context.",
                "Сделай ответ компактным и практичным: сначала вывод, затем 2-4 коротких уточнения.",
                "Если в retrieved context нет подтверждения, не делай категоричных утверждений.",
                "Приложение само прикрепит sources и quotes детерминированно, поэтому сгенерируй только answerText."
            )
        }

        val systemPromptSuffix = buildString {
            appendLine()
            appendLine("RAG MODE")
            appendLine(policyInstruction)
            profileInstructions.forEach(::appendLine)
        }.trim()

        val userPrompt = buildString {
            appendLine("Вопрос пользователя:")
            appendLine(question)
            conversationTaskBlock?.let {
                appendLine()
                appendLine("Conversation Task State:")
                appendLine(it)
            }
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
            retrievalSummary = retrievalSummaryFactory.create(retrieval, fallbackAnswerText.orEmpty()),
            fallbackAnswerText = fallbackAnswerText,
            conversationContextBlock = conversationTaskBlock,
            promptProfile = promptProfile
        )
    }
}
