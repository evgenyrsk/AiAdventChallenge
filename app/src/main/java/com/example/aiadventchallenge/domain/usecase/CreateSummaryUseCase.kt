package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.CompressionConfig
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.repository.AiRepository

class CreateSummaryUseCase(
    private val repository: AiRepository
) {

    suspend operator fun invoke(messages: List<ChatMessage>): ChatResult<AnswerWithUsage> {
        if (messages.isEmpty()) {
            return ChatResult.Error("Нет сообщений для создания summary")
        }

        val messagesForSummary = messages.takeLast(CompressionConfig.SUMMARY_INTERVAL)
        val summaryPrompt = buildSummaryPrompt(messagesForSummary)

        println("📝 Summary context:")
        val contextPreview = messagesForSummary.take(3).joinToString("\n  ") { message ->
            val role = if (message.isFromUser) "User" else "AI"
            "[$role] ${message.content}"
        }
        if (messagesForSummary.size > 3) {
            println("  $contextPreview")
            println("  ... (${messagesForSummary.size - 3} more)")
        } else {
            println("  $contextPreview")
        }

        val config = RequestConfig(
            systemPrompt = buildSystemPrompt()
        )

        return when (val result = repository.askWithUsage(summaryPrompt, null, config)) {
            is ChatResult.Success -> {
                println("  ✓ Tokens: ${result.data.totalTokens}")
                ChatResult.Success(result.data)
            }
            is ChatResult.Error -> {
                println("  ✗ Error: ${result.message}")
                result
            }
        }
    }

    private fun buildSummaryPrompt(messages: List<ChatMessage>): String {
        val messagesText = messages.joinToString("\n\n") { message ->
            val role = if (message.isFromUser) "Пользователь" else "Ассистент"
            "$role: ${message.content}"
        }

        return """Создай краткое резюме следующего диалога:

$messagesText

Требования:
- Сохрани ключевые темы и контекст разговора
- Упирайся на последние сообщения
- Отвечай на русском языке
- Будь кратким, но информативным
- Не используй форматирование и эмодзи"""
    }

    private fun buildSystemPrompt(): String {
        return """Ты — помощник по созданию резюме диалогов.

Создавай краткое и информативное резюме диалога, сохраняя ключевые темы и контекст."""
    }
}
