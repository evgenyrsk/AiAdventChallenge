package com.example.mcp.server.scheduler

import com.example.mcp.server.data.task.TaskRepository
import com.example.mcp.server.model.task.ScheduleType
import com.example.mcp.server.model.task.ScheduledTask
import com.example.mcp.server.model.task.TaskType
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.ZoneId

class TaskScheduler(
    private val repository: TaskRepository,
    private val executor: TaskExecutor,
    private val checkIntervalSeconds: Int = 10
) {

    private var schedulerJob: Job? = null
    private var isRunning = false

    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("⚠️  TaskScheduler is already running")
            return
        }

        isRunning = true
        println("📅 Starting TaskScheduler (check interval: ${checkIntervalSeconds}s)")

        schedulerJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    checkAndExecutePendingTasks()
                } catch (e: Exception) {
                    println("❌ Error in scheduler job: ${e.message}")
                    e.printStackTrace()
                }

                delay(checkIntervalSeconds * 1000L)
            }
        }
    }

    fun stop() {
        if (!isRunning) {
            println("⚠️  TaskScheduler is not running")
            return
        }

        isRunning = false
        schedulerJob?.cancel()
        println("⏹️  TaskScheduler stopped")
    }

    fun scheduleReminder(delayMinutes: Int, message: String): ScheduledTask {
        val task = ScheduledTask(
            id = ScheduledTask.generateId(),
            type = TaskType.REMINDER,
            scheduleType = ScheduleType.DELAY_MINUTES,
            message = message,
            delayMinutes = delayMinutes,
            createdAt = System.currentTimeMillis()
        )

        val success = repository.scheduleTask(task)
        if (success) {
            println("✅ Reminder scheduled: $message (in $delayMinutes minutes)")
        } else {
            println("❌ Failed to schedule reminder")
        }

        return task
    }

    fun scheduleReminderAt(scheduledTime: Long, message: String): ScheduledTask {
        val task = ScheduledTask(
            id = ScheduledTask.generateId(),
            type = TaskType.REMINDER,
            scheduleType = ScheduleType.SCHEDULED_TIME,
            message = message,
            scheduledTime = scheduledTime,
            createdAt = System.currentTimeMillis()
        )

        val success = repository.scheduleTask(task)
        if (success) {
            val dateTime = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(scheduledTime),
                ZoneId.systemDefault()
            )
            println("✅ Reminder scheduled at $dateTime: $message")
        } else {
            println("❌ Failed to schedule reminder")
        }

        return task
    }

    fun schedulePeriodicTask(periodMinutes: Int, type: TaskType, message: String? = null): ScheduledTask {
        val task = ScheduledTask(
            id = ScheduledTask.generateId(),
            type = type,
            scheduleType = ScheduleType.PERIODIC,
            message = message,
            periodMinutes = periodMinutes,
            scheduledTime = System.currentTimeMillis() + (periodMinutes * 60000L),
            createdAt = System.currentTimeMillis()
        )

        val success = repository.scheduleTask(task)
        if (success) {
            println("✅ Periodic task scheduled: ${type.name} (every $periodMinutes minutes)")
        } else {
            println("❌ Failed to schedule periodic task")
        }

        return task
    }

    fun cancelTask(taskId: String): Boolean {
        val cancelled = repository.cancelTask(taskId)
        if (cancelled) {
            println("✅ Task cancelled: $taskId")
        } else {
            println("❌ Failed to cancel task: $taskId")
        }
        return cancelled
    }

    fun runTaskNow(taskId: String): String? {
        val task = repository.getTaskById(taskId) ?: return null
        return executor.executeTask(task)
    }

    fun checkAndExecutePendingTasks(): List<String> {
        val tasksToExecute = repository.getPendingTasksDueNow()
        val results = mutableListOf<String>()

        tasksToExecute.forEach { task ->
            val result = executor.executeTask(task)
            results.add(result)

            if (task.periodMinutes != null) {
                val newScheduledTime = System.currentTimeMillis() + (task.periodMinutes!! * 60000L)
                val newTask = ScheduledTask(
                    id = ScheduledTask.generateId(),
                    type = task.type,
                    scheduleType = ScheduleType.PERIODIC,
                    message = task.message,
                    periodMinutes = task.periodMinutes,
                    scheduledTime = newScheduledTime,
                    createdAt = System.currentTimeMillis()
                )
                repository.scheduleTask(newTask)
            }
        }

        if (tasksToExecute.isNotEmpty()) {
            println("🔄 Executed ${tasksToExecute.size} pending tasks")
        }

        return results
    }

    fun getPendingTasks(): List<ScheduledTask> {
        return repository.getPendingTasks()
    }

    fun getAllTasks(): List<ScheduledTask> {
        return repository.getAllTasks()
    }

    fun getStatus(): String {
        val pendingCount = repository.getPendingTasks().size
        val totalCount = repository.getTasksCount()
        return if (isRunning) {
            "Running (pending: $pendingCount, total: $totalCount)"
        } else {
            "Stopped (pending: $pendingCount, total: $totalCount)"
        }
    }
}
