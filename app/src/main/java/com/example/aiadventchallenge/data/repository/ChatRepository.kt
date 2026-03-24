package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ChatMessageDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChatRepository(private val chatMessageDao: ChatMessageDao) {

    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    content = entity.content,
                    isFromUser = entity.isFromUser
                )
            }
        }
    }

    suspend fun insertMessage(message: ChatMessage) {
        val entity = ChatMessageEntity(
            id = message.id,
            content = message.content,
            isFromUser = message.isFromUser
        )
        chatMessageDao.insertMessage(entity)
    }

    suspend fun insertMessages(messages: List<ChatMessage>) {
        val entities = messages.map { message ->
            ChatMessageEntity(
                id = message.id,
                content = message.content,
                isFromUser = message.isFromUser
            )
        }
        entities.forEach { chatMessageDao.insertMessage(it) }
    }

    suspend fun deleteAllMessages() {
        chatMessageDao.deleteAllMessages()
    }
}
