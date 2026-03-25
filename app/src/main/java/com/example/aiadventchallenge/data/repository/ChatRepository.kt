package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ChatMessageDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    content = entity.content,
                    isFromUser = entity.isFromUser,
                    promptTokens = entity.promptTokens,
                    completionTokens = entity.completionTokens,
                    totalTokens = entity.totalTokens
                )
            }
        }
    }

    suspend fun insertMessage(message: ChatMessage) {
        val entity = ChatMessageEntity(
            id = message.id,
            content = message.content,
            isFromUser = message.isFromUser,
            promptTokens = message.promptTokens,
            completionTokens = message.completionTokens,
            totalTokens = message.totalTokens
        )
        chatMessageDao.insertMessage(entity)
    }

    suspend fun insertMessages(messages: List<ChatMessage>) {
        val entities = messages.map { message ->
            ChatMessageEntity(
                id = message.id,
                content = message.content,
                isFromUser = message.isFromUser,
                promptTokens = message.promptTokens,
                completionTokens = message.completionTokens,
                totalTokens = message.totalTokens
            )
        }
        entities.forEach { chatMessageDao.insertMessage(it) }
    }

    suspend fun getDialogStats(): DialogTokenStats {
        return chatMessageDao.getDialogStats()
    }

    suspend fun deleteAllMessages() {
        chatMessageDao.deleteAllMessages()
    }
}