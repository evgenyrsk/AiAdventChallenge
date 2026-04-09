package com.example.mcp.server.data.task

import com.example.mcp.server.model.task.ScheduleType
import com.example.mcp.server.model.task.ScheduledTask
import com.example.mcp.server.model.task.ScheduledTaskEntity
import com.example.mcp.server.model.task.TaskStatus
import com.example.mcp.server.model.task.TaskType

class TaskRepository(
    private val scheduledTaskDao: ScheduledTaskDao
) {

    fun scheduleTask(task: ScheduledTask): Boolean {
        val entity = task.toEntity()
        return scheduledTaskDao.insert(entity)
    }

    fun getPendingTasks(): List<ScheduledTask> {
        return scheduledTaskDao.getPendingTasks().map { it.toDomain() }
    }

    fun getPendingTasksDueNow(): List<ScheduledTask> {
        return scheduledTaskDao.getPendingTasksDueNow().map { it.toDomain() }
    }

    fun markTaskCompleted(taskId: String, output: String? = null): Boolean {
        return scheduledTaskDao.updateStatus(
            id = taskId,
            status = TaskStatus.COMPLETED,
            executedAt = System.currentTimeMillis(),
            errorMessage = null
        )
    }

    fun markTaskFailed(taskId: String, errorMessage: String): Boolean {
        return scheduledTaskDao.updateStatus(
            id = taskId,
            status = TaskStatus.FAILED,
            executedAt = System.currentTimeMillis(),
            errorMessage = errorMessage
        )
    }

    fun cancelTask(taskId: String): Boolean {
        return scheduledTaskDao.updateStatus(
            id = taskId,
            status = TaskStatus.CANCELLED,
            executedAt = null,
            errorMessage = null
        )
    }

    fun getAllTasks(): List<ScheduledTask> {
        return scheduledTaskDao.getAll().map { it.toDomain() }
    }

    fun getTaskById(taskId: String): ScheduledTask? {
        return scheduledTaskDao.getById(taskId)?.toDomain()
    }

    fun getTasksCount(): Int {
        return scheduledTaskDao.count()
    }
}
