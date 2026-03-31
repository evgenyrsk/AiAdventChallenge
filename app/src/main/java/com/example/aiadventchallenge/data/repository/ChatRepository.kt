package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.config.CompressionConfig
import com.example.aiadventchallenge.data.local.dao.ChatMessageDao
import com.example.aiadventchallenge.data.local.dao.BranchDao
import com.example.aiadventchallenge.data.local.dao.FactDao
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.CompressedChatHistory
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.SummaryMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val branchDao: BranchDao,
    private val factDao: FactDao
) {

    fun getAllMessages(): Flow<List<ChatMessage>> {
        return chatMessageDao.getAllMessages().map { entities ->
            entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    parentMessageId = entity.parentMessageId,
                    content = entity.content,
                    isFromUser = entity.isFromUser,
                    branchId = entity.branchId,
                    promptTokens = entity.promptTokens,
                    completionTokens = entity.completionTokens,
                    totalTokens = entity.totalTokens
                )
            }
        }
    }

    suspend fun getMessagesByBranch(branchId: String = "main"): List<ChatMessage> {
        val entities = chatMessageDao.getMessagesByBranch(branchId)

        return entities.map { entity ->
            ChatMessage(
                id = entity.id,
                parentMessageId = entity.parentMessageId,
                content = entity.content,
                isFromUser = entity.isFromUser,
                branchId = entity.branchId,
                promptTokens = entity.promptTokens,
                completionTokens = entity.completionTokens,
                totalTokens = entity.totalTokens
            )
        }
    }

    suspend fun insertMessage(message: ChatMessage, branchId: String = "main", parentMessageId: String? = null) {
        val entity = ChatMessageEntity(
            id = message.id,
            parentMessageId = parentMessageId,
            content = message.content,
            isFromUser = message.isFromUser,
            promptTokens = message.promptTokens,
            completionTokens = message.completionTokens,
            totalTokens = message.totalTokens,
            branchId = branchId
        )
        chatMessageDao.insertMessage(entity)
    }

    suspend fun insertMessages(messages: List<ChatMessage>, branchId: String = "main") {
        val entities = messages.map { message ->
            ChatMessageEntity(
                id = message.id,
                parentMessageId = message.parentMessageId,
                content = message.content,
                isFromUser = message.isFromUser,
                branchId = branchId,
                promptTokens = message.promptTokens,
                completionTokens = message.completionTokens,
                totalTokens = message.totalTokens
            )
        }
        entities.forEach { chatMessageDao.insertMessage(it) }
    }

    suspend fun getActiveBranchId(): String? {
        return branchDao.getActiveBranchId()
    }

    suspend fun getDialogStats(): DialogTokenStats {
        return chatMessageDao.getDialogStats()
    }

    suspend fun deleteAllMessages() {
        chatMessageDao.deleteAllMessages()
        chatMessageDao.deleteAllSummaries()
    }

    suspend fun deleteMessagesByBranch(branchId: String) {
        chatMessageDao.deleteMessagesByBranch(branchId)
    }

    suspend fun getMessageById(messageId: String): ChatMessage? {
        val entity = chatMessageDao.getMessageById(messageId) ?: return null
        return ChatMessage(
            id = entity.id,
            parentMessageId = entity.parentMessageId,
            content = entity.content,
            isFromUser = entity.isFromUser,
            branchId = entity.branchId,
            promptTokens = entity.promptTokens,
            completionTokens = entity.completionTokens,
            totalTokens = entity.totalTokens
        )
    }

    suspend fun getPathToRoot(messageId: String): List<ChatMessage> {
        val entities = chatMessageDao.getPathToRoot(messageId)
        return entities.map { entity ->
            ChatMessage(
                id = entity.id,
                parentMessageId = entity.parentMessageId,
                content = entity.content,
                isFromUser = entity.isFromUser,
                branchId = entity.branchId,
                promptTokens = entity.promptTokens,
                completionTokens = entity.completionTokens,
                totalTokens = entity.totalTokens
            )
        }
    }

    suspend fun getActivePath(branchId: String): List<ChatMessage> {
        val entities = chatMessageDao.getFullBranchPath(branchId)
        return entities.map { entity ->
            ChatMessage(
                id = entity.id,
                parentMessageId = entity.parentMessageId,
                content = entity.content,
                isFromUser = entity.isFromUser,
                branchId = entity.branchId,
                promptTokens = entity.promptTokens,
                completionTokens = entity.completionTokens,
                totalTokens = entity.totalTokens
            )
        }
    }

    suspend fun getBranchPathWithCheckpoint(branchId: String): List<ChatMessage> {
        val entities = chatMessageDao.getBranchPathWithCheckpoint(branchId)
        return entities.map { entity ->
            ChatMessage(
                id = entity.id,
                parentMessageId = entity.parentMessageId,
                content = entity.content,
                isFromUser = entity.isFromUser,
                branchId = entity.branchId,
                promptTokens = entity.promptTokens,
                completionTokens = entity.completionTokens,
                totalTokens = entity.totalTokens
            )
        }
    }
}
