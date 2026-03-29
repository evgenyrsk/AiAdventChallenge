package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig

class SlidingWindowStrategy(
    private val config: ContextStrategyConfig
) : ContextStrategy {

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

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
            "strategy" to "SlidingWindow",
            "windowSize" to config.windowSize
        )
    }
}
