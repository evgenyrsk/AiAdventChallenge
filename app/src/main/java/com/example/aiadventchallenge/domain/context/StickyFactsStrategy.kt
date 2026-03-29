package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.FactEntry
import com.example.aiadventchallenge.domain.repository.FactRepository
import kotlinx.coroutines.flow.first

class StickyFactsStrategy(
    private val config: ContextStrategyConfig,
    private val factRepository: FactRepository
) : ContextStrategy {

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        val facts = factRepository.getAllFacts().first()
        if (facts.isNotEmpty()) {
            val factsText = facts.joinToString("\n") { fact ->
                "${fact.key}: ${fact.value}"
            }
            result.add(
                Message(
                    MessageRole.SYSTEM,
                    "Known facts about the conversation:\n$factsText"
                )
            )
        }

        val windowMessages = messages.takeLast(config.windowSize)
        windowMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    override suspend fun onUserMessage(message: ChatMessage) {
    }

    override suspend fun onAssistantMessage(message: ChatMessage) {
    }

    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "strategy" to "StickyFacts",
            "windowSize" to config.windowSize
        )
    }
}
