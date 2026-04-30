package com.example.supportassistant.prompt

import com.example.supportassistant.model.RetrievedSupportChunk
import com.example.supportassistant.model.SupportTicketContext
import com.example.supportassistant.model.SupportUserContext
import java.io.File

class SupportPromptAssembler(
    private val promptPath: String
) {
    fun assemble(
        question: String,
        user: SupportUserContext?,
        userError: String?,
        ticket: SupportTicketContext?,
        ticketError: String?,
        retrievedChunks: List<RetrievedSupportChunk>,
        mismatchWarning: String?
    ): String {
        val template = loadTemplate()
        return buildString {
            appendLine(template)
            appendLine()
            appendLine("Вопрос пользователя:")
            appendLine(question)
            appendLine()
            appendLine("Данные пользователя:")
            appendLine(user?.toPromptSafeString() ?: (userError ?: "Не переданы или не найдены."))
            appendLine()
            appendLine("Данные тикета:")
            appendLine(ticket?.toPromptString() ?: (ticketError ?: "Не переданы или не найдены."))
            if (!mismatchWarning.isNullOrBlank()) {
                appendLine()
                appendLine("Важное предупреждение:")
                appendLine(mismatchWarning)
            }
            appendLine()
            appendLine("FAQ/docs context:")
            if (retrievedChunks.isEmpty()) {
                appendLine("Релевантные документы не найдены. Не выдумывай источники.")
            } else {
                retrievedChunks.forEachIndexed { index, chunk ->
                    appendLine("[${index + 1}] ${chunk.entry.title} | ${chunk.entry.section} | ${chunk.entry.scope} | score=${"%.2f".format(chunk.score)}")
                    appendLine(chunk.text)
                    appendLine("Рекомендованные действия: ${chunk.entry.suggestedActions.joinToString("; ")}")
                    appendLine()
                }
            }
            appendLine("Ответь строго в формате из инструкции.")
        }.trim()
    }

    private fun loadTemplate(): String {
        val file = File(promptPath)
        return if (file.exists()) {
            file.readText().trim()
        } else {
            "Ты — ассистент поддержки продукта “фитнес-ассистент”. Не выдумывай данные и отвечай по FAQ/docs."
        }
    }

    private fun SupportUserContext.toPromptSafeString(): String {
        return "id=$id, subscriptionStatus=$subscriptionStatus, authProvider=$authProvider, lastLoginAt=$lastLoginAt, appVersion=$appVersion, platform=$platform"
    }

    private fun SupportTicketContext.toPromptString(): String {
        return "id=$id, userId=$userId, status=$status, category=$category, message=$message, createdAt=$createdAt, metadata=$metadata"
    }
}
