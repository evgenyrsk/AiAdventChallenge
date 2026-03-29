package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.example.aiadventchallenge.data.local.entity.FactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FactDao {
    @Query("SELECT * FROM facts ORDER BY updatedAt DESC")
    fun getAllFacts(): Flow<List<FactEntity>>

    @Query("SELECT * FROM facts WHERE key = :key LIMIT 1")
    suspend fun getFactByKey(key: String): FactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFact(fact: FactEntity)

    @Update
    suspend fun updateFact(fact: FactEntity)

    @Query("DELETE FROM facts WHERE key = :key")
    suspend fun deleteFact(key: String)

    @Query("DELETE FROM facts")
    suspend fun clearAllFacts()
}
