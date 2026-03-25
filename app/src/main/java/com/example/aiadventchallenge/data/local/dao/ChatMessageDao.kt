package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.aiadventchallenge.data.local.entity.ChatMessageEntity
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

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
}