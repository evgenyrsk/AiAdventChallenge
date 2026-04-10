package com.example.mcp.server.model.reminder

import kotlinx.serialization.Serializable

@Serializable
data class Reminder(
    val id: String,
    val type: ReminderType,
    val title: String,
    val message: String,
    val time: String,
    val daysOfWeek: List<DayOfWeek>,
    val isActive: Boolean,
    val createdAt: Long
) {
    fun toEntity(): ReminderEntity {
        return ReminderEntity(
            id = id,
            type = type.name,
            title = title,
            message = message,
            time = time,
            daysOfWeek = daysOfWeek.map { it.name }.joinToString(","),
            isActive = isActive,
            createdAt = createdAt
        )
    }

    companion object {
        fun generateId(): String {
            return "reminder_${System.currentTimeMillis()}_${(0..9999).random()}"
        }
    }
}

@Serializable
enum class ReminderType {
    WORKOUT,
    HYDRATION,
    PROTEIN,
    SLEEP
}

@Serializable
enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

data class ReminderEntity(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val time: String,
    val daysOfWeek: String,
    val isActive: Boolean,
    val createdAt: Long
) {
    fun toDomain(): Reminder {
        return Reminder(
            id = id,
            type = ReminderType.valueOf(type),
            title = title,
            message = message,
            time = time,
            daysOfWeek = daysOfWeek.split(",").map { DayOfWeek.valueOf(it.trim()) },
            isActive = isActive,
            createdAt = createdAt
        )
    }
}