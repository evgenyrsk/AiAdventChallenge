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
                    content = entity.content,
                    isFromUser = entity.isFromUser,
                    promptTokens = entity.promptTokens,
                    completionTokens = entity.completionTokens,
                    totalTokens = entity.totalTokens
                )
            }
        }
    }

    suspend fun getMessagesByBranch(branchId: String?): List<ChatMessage> {
        val entities = if (branchId != null) {
            chatMessageDao.getMessagesByBranch(branchId)
        } else {
            chatMessageDao.getAllMessagesList()
        }

        return entities.map { entity ->
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

    suspend fun getCompressedHistory(): CompressedChatHistory {
        val summaryEntities = chatMessageDao.getAllSummaries()
        val summaries = summaryEntities.map { entity ->
            SummaryMessage(
                id = entity.id,
                content = entity.content,
                messageRangeStart = entity.messageRangeStart,
                messageRangeEnd = entity.messageRangeEnd,
                messageCount = entity.messageCount,
                createdAt = entity.createdAt
            )
        }

        val nonSummarizedEntities = chatMessageDao.getNonSummarizedMessages()
        val nonSummarizedMessages = nonSummarizedEntities.map { entity ->
            ChatMessage(
                id = entity.id,
                content = entity.content,
                isFromUser = entity.isFromUser,
                promptTokens = entity.promptTokens,
                completionTokens = entity.completionTokens,
                totalTokens = entity.totalTokens
            )
        }

        val recentMessages = nonSummarizedMessages.takeLast(CompressionConfig.RECENT_MESSAGES_LIMIT)

        println("📊 Compressed history:")
        println("  Summaries: ${summaries.size}")
        println("  Non-summarized messages: ${nonSummarizedMessages.size}")
        println("  Recent messages: ${recentMessages.size}")

        return CompressedChatHistory(
            summaries = summaries,
            recentMessages = recentMessages
        )
    }

    suspend fun insertMessage(message: ChatMessage, branchId: String? = null) {
        val entity = ChatMessageEntity(
            id = message.id,
            content = message.content,
            isFromUser = message.isFromUser,
            promptTokens = message.promptTokens,
            completionTokens = message.completionTokens,
            totalTokens = message.totalTokens,
            branchId = branchId
        )
        chatMessageDao.insertMessage(entity)
    }

    suspend fun insertMessages(messages: List<ChatMessage>, branchId: String? = null) {
        val entities = messages.map { message ->
            ChatMessageEntity(
                id = message.id,
                content = message.content,
                isFromUser = message.isFromUser,
                promptTokens = message.promptTokens,
                completionTokens = message.completionTokens,
                totalTokens = message.totalTokens,
                branchId = branchId
            )
        }
        entities.forEach { chatMessageDao.insertMessage(it) }
    }

    suspend fun getActiveBranchId(): String? {
        return branchDao.getActiveBranchId()
    }

    suspend fun insertSummary(summary: SummaryMessage) {
        val entity = SummaryEntity(
            id = summary.id,
            content = summary.content,
            messageRangeStart = summary.messageRangeStart,
            messageRangeEnd = summary.messageRangeEnd,
            messageCount = summary.messageCount,
            createdAt = summary.createdAt
        )
        chatMessageDao.insertSummary(entity)
    }

    suspend fun deleteSummaryByRangeEnd(messageRangeEnd: Long) {
        chatMessageDao.deleteSummaryByRangeEnd(messageRangeEnd)
    }

    suspend fun getAllSummaries(): List<SummaryMessage> {
        return chatMessageDao.getAllSummaries().map { entity ->
            SummaryMessage(
                id = entity.id,
                content = entity.content,
                messageRangeStart = entity.messageRangeStart,
                messageRangeEnd = entity.messageRangeEnd,
                messageCount = entity.messageCount,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun shouldCreateSummary(): Boolean {
        val messageCount = chatMessageDao.getMessageCount()
        return messageCount >= CompressionConfig.RECENT_MESSAGES_LIMIT &&
               messageCount % (CompressionConfig.SUMMARY_INTERVAL) == 0
    }

    suspend fun getDialogStats(): DialogTokenStats {
        return chatMessageDao.getDialogStats()
    }

    suspend fun deleteAllMessages() {
        chatMessageDao.deleteAllMessages()
        chatMessageDao.deleteAllSummaries()
    }
}
