package com.example.mcp.server.model.task

import kotlinx.serialization.Serializable

@Serializable
enum class TaskType {
    REMINDER,
    FITNESS_SUMMARY,
    DATA_COLLECTION,
    CUSTOM
}

@Serializable
enum class ScheduleType {
    DELAY_MINUTES,      // "через 5 минут"
    SCHEDULED_TIME,     // "завтра в 9:00"
    PERIODIC            // "каждые 5 минут"
}

@Serializable
enum class TaskStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    FAILED
}

@Serializable
data class ScheduledTask(
    val id: String,
    val type: TaskType,
    val scheduleType: ScheduleType,
    val message: String? = null,
    val delayMinutes: Int? = null,
    val scheduledTime: Long? = null,
    val periodMinutes: Int? = null,
    val createdAt: Long,
    val status: TaskStatus = TaskStatus.PENDING,
    val executedAt: Long? = null,
    val errorMessage: String? = null
) {
    fun toEntity(): ScheduledTaskEntity {
        return ScheduledTaskEntity(
            id = id,
            type = type,
            scheduleType = scheduleType,
            message = message,
            delayMinutes = delayMinutes,
            scheduledTime = scheduledTime,
            periodMinutes = periodMinutes,
            createdAt = createdAt,
            status = status,
            executedAt = executedAt,
            errorMessage = errorMessage
        )
    }

    companion object {
        fun generateId(): String {
            return "task_${System.currentTimeMillis()}_${(0..9999).random()}"
        }
    }
}

data class ScheduledTaskEntity(
    val id: String,
    val type: TaskType,
    val scheduleType: ScheduleType,
    val message: String?,
    val delayMinutes: Int?,
    val scheduledTime: Long?,
    val periodMinutes: Int?,
    val createdAt: Long,
    val status: TaskStatus,
    val executedAt: Long?,
    val errorMessage: String?
) {
    fun toDomain(): ScheduledTask {
        return ScheduledTask(
            id = id,
            type = type,
            scheduleType = scheduleType,
            message = message,
            delayMinutes = delayMinutes,
            scheduledTime = scheduledTime,
            periodMinutes = periodMinutes,
            createdAt = createdAt,
            status = status,
            executedAt = executedAt,
            errorMessage = errorMessage
        )
    }
}
