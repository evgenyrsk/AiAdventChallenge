package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.data.local.entity.SummaryEntity
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllMessagesList(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE branchId = :branchId ORDER BY timestamp ASC")
    suspend fun getMessagesByBranch(branchId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()

    @Query("""
        SELECT 
            COUNT(*) as requestsCount,
            COALESCE(SUM(promptTokens), 0) as totalPromptTokens,
            COALESCE(SUM(completionTokens), 0) as totalCompletionTokens,
            COALESCE(SUM(totalTokens), 0) as totalTokens
        FROM chat_messages 
        WHERE isFromUser = 0
    """)
    fun getDialogStats(): DialogTokenStats

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM summaries ORDER BY createdAt ASC")
    suspend fun getAllSummaries(): List<SummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: SummaryEntity)

    @Query("DELETE FROM summaries WHERE messageRangeEnd = :messageRangeEnd")
    suspend fun deleteSummaryByRangeEnd(messageRangeEnd: Long)

    @Query("DELETE FROM summaries")
    suspend fun deleteAllSummaries()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int

    @Query("""
        SELECT cm.* FROM chat_messages cm
        WHERE NOT EXISTS (
            SELECT 1 FROM summaries sm
            WHERE cm.id >= sm.messageRangeStart AND cm.id <= sm.messageRangeEnd
        )
        ORDER BY cm.timestamp ASC
    """)
    suspend fun getNonSummarizedMessages(): List<ChatMessageEntity>
}
