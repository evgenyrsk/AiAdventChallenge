package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskAction
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun createTask(query: String, profile: FitnessProfileType = FitnessProfileType.INTERMEDIATE): TaskContext
    suspend fun updateTask(taskId: String, action: TaskAction): TaskContext?
    suspend fun getActiveTask(): TaskContext?
    suspend fun getTaskById(taskId: String): TaskContext?
    fun getAllTasks(): Flow<List<TaskContext>>
    suspend fun setActiveTask(taskId: String)
    suspend fun deactivateAllTasks()
    suspend fun deleteTask(taskId: String)
    suspend fun clearAllTasks()
    suspend fun getTaskHistory(taskId: String): Flow<List<TaskContext>>
}
