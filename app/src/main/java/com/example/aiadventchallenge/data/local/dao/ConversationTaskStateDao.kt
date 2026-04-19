package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiadventchallenge.data.local.entity.ConversationTaskStateEntity

@Dao
interface ConversationTaskStateDao {
    @Query("SELECT * FROM conversation_task_state WHERE branchId = :branchId LIMIT 1")
    suspend fun getByBranchId(branchId: String): ConversationTaskStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationTaskStateEntity)

    @Query("DELETE FROM conversation_task_state WHERE branchId = :branchId")
    suspend fun deleteByBranchId(branchId: String)

    @Query("DELETE FROM conversation_task_state")
    suspend fun deleteAll()
}
