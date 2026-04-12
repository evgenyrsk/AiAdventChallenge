package com.example.aiadventchallenge.domain.context

import android.util.Log
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig

class SlidingWindowStrategy(
    private val config: ContextStrategyConfig
) : ContextStrategy {

    private var filteredMessagesCount: Int = 0

    init {
        require(config.windowSize > 0) { "windowSize must be positive, got ${config.windowSize}" }
    }

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        val windowMessages = messages.takeLast(config.windowSize)
        filteredMessagesCount = messages.size - windowMessages.size

        println("📊 SlidingWindow context:")
        println("  Total messages: ${messages.size}")
        println("  Window size: ${config.windowSize}")
        println("  Messages in context: ${windowMessages.size}")
        println("  Messages filtered: $filteredMessagesCount")

        windowMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    override suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        println("📥 Conversation pair received")
    }

    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "strategy" to "SlidingWindow",
            "windowSize" to config.windowSize,
            "filteredMessagesCount" to filteredMessagesCount,
            "messagesInContext" to config.windowSize
        )
    }
}
