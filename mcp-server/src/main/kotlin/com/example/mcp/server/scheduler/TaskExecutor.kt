package com.example.mcp.server.scheduler

import com.example.mcp.server.data.task.TaskRepository
import com.example.mcp.server.model.task.ScheduledTask
import com.example.mcp.server.model.task.TaskType

class TaskExecutor(
    private val taskRepository: TaskRepository,
    private val backgroundSummaryScheduler: BackgroundSummaryScheduler?
) {

    fun executeTask(task: ScheduledTask): String {
        return try {
            val result = when (task.type) {
                TaskType.REMINDER -> executeReminder(task)
                TaskType.FITNESS_SUMMARY -> executeFitnessSummary(task)
                TaskType.DATA_COLLECTION -> executeDataCollection(task)
                TaskType.CUSTOM -> executeCustomTask(task)
            }

            taskRepository.markTaskCompleted(task.id, result)
            result
        } catch (e: Exception) {
            taskRepository.markTaskFailed(task.id, e.message ?: "Unknown error")
            "Error: ${e.message}"
        }
    }

    private fun executeReminder(task: ScheduledTask): String {
        val message = task.message ?: "Напоминание"
        println("🔔 REMINDER: $message")
        return "Напоминание выполнено: $message"
    }

    private fun executeFitnessSummary(task: ScheduledTask): String {
        if (backgroundSummaryScheduler != null) {
            val summary = backgroundSummaryScheduler.runScheduledSummaryNow()
            if (summary != null) {
                println("📊 FITNESS SUMMARY: ${summary.summaryText}")
                return "Fitness summary: ${summary.summaryText}"
            } else {
                return "Нет данных для генерации summary"
            }
        } else {
            return "FitnessSummaryScheduler не подключён"
        }
    }

    private fun executeDataCollection(task: ScheduledTask): String {
        val message = task.message ?: "Сбор данных"
        println("📊 DATA COLLECTION: $message")
        return "Сбор данных выполнен: $message"
    }

    private fun executeCustomTask(task: ScheduledTask): String {
        val message = task.message ?: "Пользовательская задача"
        println("⚙️  CUSTOM TASK: $message")
        return "Пользовательская задача выполнена: $message"
    }
}
