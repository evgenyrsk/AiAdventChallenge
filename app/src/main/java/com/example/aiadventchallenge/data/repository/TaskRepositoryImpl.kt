package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.TaskDao
import com.example.aiadventchallenge.data.mapper.TaskMapper
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskAction
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskStateMachine
import com.example.aiadventchallenge.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val stateMachine: TaskStateMachine = TaskStateMachine()
) : TaskRepository {

    override suspend fun createTask(query: String, profile: FitnessProfileType): TaskContext {
        deactivateAllTasks()
        val task = TaskContext.create(query, profile)
        taskDao.insert(TaskMapper.toEntity(task))
        return task
    }

    override suspend fun updateTask(taskId: String, action: TaskAction): TaskContext? {
        val currentTask = getTaskById(taskId) ?: return null
        val updatedTask = stateMachine.transition(currentTask, action)
        taskDao.insert(TaskMapper.toEntity(updatedTask))
        return updatedTask
    }

    override suspend fun getActiveTask(): TaskContext? {
        val entity = taskDao.getActiveTask() ?: return null
        return TaskMapper.toDomain(entity)
    }

    override suspend fun getTaskById(taskId: String): TaskContext? {
        val entity = taskDao.getTaskById(taskId) ?: return null
        return TaskMapper.toDomain(entity)
    }

    override fun getAllTasks(): Flow<List<TaskContext>> {
        return taskDao.getAllTasks().map { entities ->
            TaskMapper.toDomainList(entities)
        }
    }

    override suspend fun setActiveTask(taskId: String) {
        deactivateAllTasks()
        taskDao.setActive(taskId)
    }

    override suspend fun deactivateAllTasks() {
        taskDao.deactivateAll()
    }

    override suspend fun deleteTask(taskId: String) {
        taskDao.delete(taskId)
    }

    override suspend fun clearAllTasks() {
        taskDao.deleteAll()
    }

    override suspend fun getTaskHistory(taskId: String): Flow<List<TaskContext>> {
        return taskDao.getTaskHistory(taskId).map { entities ->
            TaskMapper.toDomainList(entities)
        }
    }
}
