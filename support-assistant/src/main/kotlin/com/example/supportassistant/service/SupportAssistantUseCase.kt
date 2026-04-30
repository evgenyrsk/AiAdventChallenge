package com.example.supportassistant.service

import com.example.supportassistant.config.SupportConfig
import com.example.supportassistant.llm.SupportLlmClient
import com.example.supportassistant.logging.SupportRequestLogger
import com.example.supportassistant.mcp.JsonMockCrmMcp
import com.example.supportassistant.model.RetrievedSupportChunk
import com.example.supportassistant.model.SupportAskRequest
import com.example.supportassistant.model.SupportAskResponse
import com.example.supportassistant.model.SupportSource
import com.example.supportassistant.model.SupportTicketContext
import com.example.supportassistant.model.SupportUserContext
import com.example.supportassistant.prompt.SupportPromptAssembler
import com.example.supportassistant.rag.SupportRagRetriever

class SupportAssistantUseCase(
    private val config: SupportConfig,
    private val mcp: JsonMockCrmMcp,
    private val retriever: SupportRagRetriever,
    private val promptAssembler: SupportPromptAssembler,
    private val llmClient: SupportLlmClient,
    private val logger: SupportRequestLogger = SupportRequestLogger()
) {
    suspend fun ask(request: SupportAskRequest): SupportAskResponse {
        val question = request.question.trim()
        if (question.isBlank()) {
            throw InvalidSupportRequestException("question must not be blank")
        }

        logger.request(request.userId, request.ticketId)

        val userResult = request.userId?.takeIf { it.isNotBlank() }?.let {
            mcp.getUserById(it).also { result -> logger.mcp(result.tool, result.ok) }
        }
        val ticketResult = request.ticketId?.takeIf { it.isNotBlank() }?.let {
            mcp.getTicketById(it).also { result -> logger.mcp(result.tool, result.ok) }
        }
        val user = userResult?.data
        val ticket = ticketResult?.data
        val mismatchWarning = buildMismatchWarning(request.userId, user, ticket)

        val chunks = retriever.retrieve(question)
        logger.retrieval(chunks.size)

        val prompt = promptAssembler.assemble(
            question = question,
            user = user,
            userError = userResult?.error,
            ticket = ticket,
            ticketError = ticketResult?.error,
            retrievedChunks = chunks,
            mismatchWarning = mismatchWarning
        )

        return try {
            val llm = llmClient.generate(prompt)
            logger.llmSuccess(config.llmBackend, llm.latencyMs)
            SupportAskResponse(
                answer = llm.content,
                sources = chunks.toSources(),
                ticketContextUsed = ticket != null,
                userContextUsed = user != null,
                suggestedActions = suggestedActions(chunks, userResult?.error, ticketResult?.error, mismatchWarning),
                confidence = extractConfidence(llm.content, chunks, user, ticket)
            )
        } catch (error: SupportLlmUnavailableException) {
            logger.failure(error::class.simpleName ?: "SupportLlmUnavailableException", error.message ?: "LLM unavailable")
            degradedResponse(question, user, userResult?.error, ticket, ticketResult?.error, chunks, mismatchWarning)
        }
    }

    private fun buildMismatchWarning(
        requestedUserId: String?,
        user: SupportUserContext?,
        ticket: SupportTicketContext?
    ): String? {
        if (ticket == null) return null
        val expectedUserId = requestedUserId?.takeIf { it.isNotBlank() } ?: user?.id
        return if (expectedUserId != null && ticket.userId != expectedUserId) {
            "ticket.userId=${ticket.userId} не совпадает с requested/userId=$expectedUserId. Не смешивай данные разных пользователей."
        } else {
            null
        }
    }

    private fun degradedResponse(
        question: String,
        user: SupportUserContext?,
        userError: String?,
        ticket: SupportTicketContext?,
        ticketError: String?,
        chunks: List<RetrievedSupportChunk>,
        mismatchWarning: String?
    ): SupportAskResponse {
        val likelyReason = chunks.firstOrNull()?.entry?.answer
            ?: "Релевантная статья поддержки не найдена, поэтому нужно уточнить детали проблемы."
        val contextNotes = buildList {
            if (user != null) add("учтен пользователь: subscriptionStatus=${user.subscriptionStatus}, authProvider=${user.authProvider}, appVersion=${user.appVersion}, platform=${user.platform}")
            if (ticket != null) add("учтен тикет: category=${ticket.category}, status=${ticket.status}, metadata=${ticket.metadata}")
            if (userError != null) add(userError)
            if (ticketError != null) add(ticketError)
            if (mismatchWarning != null) add(mismatchWarning)
        }
        val answer = buildString {
            appendLine("Краткий ответ")
            appendLine("LLM backend сейчас недоступен, поэтому возвращаю диагностический ответ по support FAQ и mock CRM context.")
            appendLine()
            appendLine("Вероятная причина")
            appendLine(likelyReason)
            appendLine()
            appendLine("Что сделать пользователю")
            suggestedActions(chunks, userError, ticketError, mismatchWarning).take(4).forEach { appendLine("- $it") }
            appendLine()
            appendLine("Что проверить поддержке")
            if (contextNotes.isEmpty()) {
                appendLine("- Уточнить userId/ticketId, appVersion, platform и код ошибки.")
            } else {
                contextNotes.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Источники")
            if (chunks.isEmpty()) appendLine("- Источники не найдены.") else chunks.forEach { appendLine("- ${it.entry.title} (${it.entry.scope}/${it.entry.section})") }
            appendLine()
            appendLine("Уверенность: low")
        }.trim()
        return SupportAskResponse(
            answer = answer,
            sources = chunks.toSources(),
            ticketContextUsed = ticket != null,
            userContextUsed = user != null,
            suggestedActions = suggestedActions(chunks, userError, ticketError, mismatchWarning),
            confidence = "low"
        )
    }

    private fun suggestedActions(
        chunks: List<RetrievedSupportChunk>,
        userError: String?,
        ticketError: String?,
        mismatchWarning: String?
    ): List<String> {
        val actions = linkedSetOf<String>()
        if (userError != null) actions += "Проверить корректность userId или запросить актуальный идентификатор пользователя."
        if (ticketError != null) actions += "Проверить корректность ticketId или открыть новый тикет поддержки."
        if (mismatchWarning != null) actions += "Не смешивать контекст тикета и пользователя до проверки принадлежности тикета."
        chunks.flatMap { it.entry.suggestedActions }.forEach { actions += it }
        if (actions.isEmpty()) actions += "Уточнить appVersion, platform, шаги воспроизведения и код ошибки."
        return actions.take(6)
    }

    private fun List<RetrievedSupportChunk>.toSources(): List<SupportSource> {
        return map {
            SupportSource(
                title = it.entry.title,
                path = "support-assistant/data/faq.json",
                section = it.entry.section,
                scope = it.entry.scope,
                score = it.score
            )
        }
    }

    private fun extractConfidence(
        answer: String,
        chunks: List<RetrievedSupportChunk>,
        user: SupportUserContext?,
        ticket: SupportTicketContext?
    ): String {
        val explicit = Regex("Уверенность:\\s*(high|medium|low)", RegexOption.IGNORE_CASE)
            .find(answer)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
        if (explicit in setOf("high", "medium", "low")) return explicit!!
        return when {
            chunks.isNotEmpty() && user != null && ticket != null -> "medium"
            chunks.isNotEmpty() -> "medium"
            else -> "low"
        }
    }
}
