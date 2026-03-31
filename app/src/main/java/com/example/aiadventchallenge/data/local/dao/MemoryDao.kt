package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aiadventchallenge.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries WHERE memoryType = :type AND branchId = :branchId AND isActive = 1 ORDER BY importance DESC")
    fun getActiveEntriesByType(type: String, branchId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entries WHERE branchId = :branchId AND isActive = 1 ORDER BY importance DESC")
    fun getActiveEntriesByBranch(branchId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: String): MemoryEntity?

    @Query("SELECT * FROM memory_entries WHERE key = :key AND branchId = :branchId AND isActive = 1 LIMIT 1")
    suspend fun getEntryByKey(key: String, branchId: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: MemoryEntity)

    @Update
    suspend fun updateEntry(entry: MemoryEntity)

    @Query("UPDATE memory_entries SET isActive = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun deactivateEntry(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE memory_entries SET isActive = 0 WHERE ttl < :now")
    suspend fun deactivateExpiredEntries(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memory_entries WHERE branchId = :branchId")
    suspend fun clearByBranch(branchId: String)

    @Query("DELETE FROM memory_entries WHERE branchId = :branchId AND memoryType = :type")
    suspend fun clearByBranchAndType(branchId: String, type: String)

    @Query("SELECT * FROM memory_entries WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun getAllEntriesByBranch(branchId: String): Flow<List<MemoryEntity>>

    @Query("SELECT COUNT(*) FROM memory_entries WHERE branchId = :branchId")
    suspend fun getTotalCountByBranch(branchId: String): Int

    @Query("SELECT COUNT(*) FROM memory_entries WHERE branchId = :branchId AND isActive = 1")
    suspend fun getActiveCountByBranch(branchId: String): Int
}