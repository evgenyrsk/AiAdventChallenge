package com.example.aiadventchallenge.data.mapper

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage

object MessageMapper {

    fun mapToApiMessages(
        chatMessages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        chatMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    fun mapToApiMessage(chatMessage: ChatMessage): Message {
        val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
        return Message(role, chatMessage.content)
    }
}
