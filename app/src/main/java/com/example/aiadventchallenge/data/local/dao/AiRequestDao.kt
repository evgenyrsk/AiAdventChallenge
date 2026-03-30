package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiadventchallenge.data.local.entity.AiRequestEntity
import com.example.aiadventchallenge.domain.model.DialogTokenStats

@Dao
interface AiRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: AiRequestEntity)

    @Query("""
        SELECT 
            COUNT(*) as requestsCount,
            COALESCE(SUM(promptTokens), 0) as totalPromptTokens,
            COALESCE(SUM(completionTokens), 0) as totalCompletionTokens,
            COALESCE(SUM(totalTokens), 0) as totalTokens
        FROM ai_requests 
    """)
    suspend fun getAllTimeStats(): DialogTokenStats

    @Query("DELETE FROM ai_requests")
    suspend fun deleteAllRequests()

    @Query("SELECT * FROM ai_requests ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentRequests(limit: Int): List<AiRequestEntity>

    @Query("SELECT COUNT(*) FROM ai_requests")
    suspend fun getRequestCount(): Int
}
