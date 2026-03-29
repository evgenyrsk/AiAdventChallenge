package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.aiadventchallenge.data.local.entity.BranchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY createdAt DESC")
    fun getAllBranches(): Flow<List<BranchEntity>>

    @Query("SELECT * FROM branches WHERE id = :branchId LIMIT 1")
    suspend fun getBranchById(branchId: String): BranchEntity?

    @Query("SELECT id FROM branches WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveBranchId(): String?

    @Query("UPDATE branches SET isActive = 0")
    suspend fun deactivateAllBranches()

    @Query("UPDATE branches SET isActive = 1 WHERE id = :branchId")
    suspend fun activateBranch(branchId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: BranchEntity)

    @Update
    suspend fun updateBranch(branch: BranchEntity)

    @Query("DELETE FROM branches WHERE id = :branchId")
    suspend fun deleteBranch(branchId: String)

    @Query("DELETE FROM branches")
    suspend fun clearAllBranches()
}
