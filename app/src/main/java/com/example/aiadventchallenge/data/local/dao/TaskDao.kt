package com.example.aiadventchallenge.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aiadventchallenge.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)
    
    @Update
    suspend fun update(task: TaskEntity)
    
    @Query("SELECT * FROM tasks WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTask(): TaskEntity?
    
    @Query("SELECT * FROM tasks WHERE taskId = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?
    
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>
    
    @Query("SELECT * FROM tasks WHERE taskId = :taskId ORDER BY updatedAt DESC")
    fun getTaskHistory(taskId: String): Flow<List<TaskEntity>>
    
    @Query("UPDATE tasks SET isActive = 0")
    suspend fun deactivateAll()
    
    @Query("UPDATE tasks SET isActive = 1 WHERE taskId = :taskId")
    suspend fun setActive(taskId: String)
    
    @Query("DELETE FROM tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
    
    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int
}
