package com.example.mcp.server.model.reminder

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class ReminderEvent(
    val id: String,
    val reminderId: String,
    val type: ReminderType,
    val scheduledTime: Long,
    val triggeredAt: Long?,
    val status: EventStatus,
    val context: ReminderContext?,
    val response: String?
) {
    fun toEntity(): ReminderEventEntity {
        return ReminderEventEntity(
            id = id,
            reminderId = reminderId,
            type = type.name,
            scheduledTime = scheduledTime,
            triggeredAt = triggeredAt,
            status = status.name,
            context = if (context != null) kotlinx.serialization.json.Json.encodeToString(context) else null,
            response = response
        )
    }

    companion object {
        fun generateId(): String {
            return "reminder_event_${System.currentTimeMillis()}_${(0..9999).random()}"
        }
    }
}

@Serializable
data class ReminderContext(
    val workoutsToday: Int? = null,
    val lastWorkoutDate: String? = null,
    val caloriesToday: Int? = null,
    val proteinToday: Int? = null,
    val sleepLastNight: Double? = null
)

@Serializable
enum class EventStatus {
    PENDING,
    TRIGGERED,
    SKIPPED
}

data class ReminderEventEntity(
    val id: String,
    val reminderId: String,
    val type: String,
    val scheduledTime: Long,
    val triggeredAt: Long?,
    val status: String,
    val context: String?,
    val response: String?
) {
    fun toDomain(): ReminderEvent {
        val context = context?.let {
            kotlinx.serialization.json.Json { 
                ignoreUnknownKeys = true 
            }.decodeFromString<ReminderContext>(it)
        }

        return ReminderEvent(
            id = id,
            reminderId = reminderId,
            type = ReminderType.valueOf(type),
            scheduledTime = scheduledTime,
            triggeredAt = triggeredAt,
            status = EventStatus.valueOf(status),
            context = context,
            response = response
        )
    }
}