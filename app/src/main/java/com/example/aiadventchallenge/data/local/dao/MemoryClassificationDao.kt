package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryClassificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassification(entity: MemoryClassificationEntity)

    @Query("SELECT * FROM memory_classifications WHERE branchId = :branchId ORDER BY createdAt DESC LIMIT 50")
    fun getRecentClassifications(branchId: String): Flow<List<MemoryClassificationEntity>>

    @Query("SELECT * FROM memory_classifications ORDER BY createdAt DESC LIMIT 100")
    fun getAllRecentClassifications(): Flow<List<MemoryClassificationEntity>>

    @Query("DELETE FROM memory_classifications WHERE branchId = :branchId")
    suspend fun clearByBranch(branchId: String)

    @Query("DELETE FROM memory_classifications")
    suspend fun clearAll()
}