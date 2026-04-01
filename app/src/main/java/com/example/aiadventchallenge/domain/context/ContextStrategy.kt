package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.model.ChatMessage

interface ContextStrategy {

    suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message>

    suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage)

    fun getDebugInfo(): Map<String, Any>
}
