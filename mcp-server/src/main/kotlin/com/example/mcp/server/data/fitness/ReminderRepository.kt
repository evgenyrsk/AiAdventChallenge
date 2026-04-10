package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.reminder.Reminder
import com.example.mcp.server.model.reminder.ReminderEntity
import com.example.mcp.server.model.reminder.ReminderEvent
import com.example.mcp.server.model.reminder.ReminderEventEntity
import com.example.mcp.server.model.reminder.ReminderType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val reminderEventDao: ReminderEventDao
) {

    fun addReminder(reminder: Reminder): Boolean {
        val entity = reminder.toEntity()
        return reminderDao.insert(entity)
    }

    fun getReminderById(id: String): Reminder? {
        return reminderDao.getById(id)?.toDomain()
    }

    fun getAllActiveReminders(): List<Reminder> {
        return reminderDao.getAllActive().map { it.toDomain() }
    }

    fun getAllReminders(): List<Reminder> {
        return reminderDao.getAll().map { it.toDomain() }
    }

    fun deleteReminder(id: String): Boolean {
        return reminderDao.delete(id)
    }

    fun getRemindersByType(type: ReminderType): List<Reminder> {
        return getAllActiveReminders().filter { it.type == type }
    }

    fun getRemindersForDayOfWeek(dayOfWeek: java.time.DayOfWeek): List<Reminder> {
        return getAllActiveReminders().filter { reminder ->
            reminder.daysOfWeek.any { reminderDay ->
                reminderDay.name == dayOfWeek.name
            }
        }
    }

    fun getRemindersDueAtTime(time: LocalTime): List<Reminder> {
        return getAllActiveReminders().filter { reminder ->
            val reminderTime = LocalTime.parse(reminder.time)
            reminderTime.hour == time.hour && reminderTime.minute == time.minute
        }
    }

    fun getDueReminders(date: LocalDate, time: LocalTime): List<Reminder> {
        val dayOfWeekName = date.dayOfWeek.name

        return getAllActiveReminders().filter { reminder ->
            val reminderTime = LocalTime.parse(reminder.time)
            reminder.daysOfWeek.any { it.name == dayOfWeekName } &&
            reminderTime.hour == time.hour &&
            reminderTime.minute == time.minute
        }
    }

    fun addReminderEvent(event: ReminderEvent): Boolean {
        val entity = event.toEntity()
        return reminderEventDao.insert(entity)
    }

    fun getReminderEventById(id: String): ReminderEvent? {
        return reminderEventDao.getById(id)?.toDomain()
    }

    fun getEventsByReminderId(reminderId: String, limit: Int = 10): List<ReminderEvent> {
        return reminderEventDao.getByReminderId(reminderId, limit).map { it.toDomain() }
    }

    fun getPendingEvents(): List<ReminderEvent> {
        return reminderEventDao.getPendingEvents().map { it.toDomain() }
    }

    fun getEventsInDateRange(startTime: Long, endTime: Long): List<ReminderEvent> {
        return reminderEventDao.getEventsInDateRange(startTime, endTime).map { it.toDomain() }
    }

    fun updateEventStatus(id: String, status: com.example.mcp.server.model.reminder.EventStatus, triggeredAt: Long?): Boolean {
        return reminderEventDao.updateStatus(id, status.name, triggeredAt)
    }

    fun updateEventResponse(id: String, response: String?): Boolean {
        return reminderEventDao.updateResponse(id, response)
    }

    fun deleteReminderEvent(id: String): Boolean {
        return reminderEventDao.delete(id)
    }

    fun markEventAsTriggered(id: String): Boolean {
        return updateEventStatus(id, com.example.mcp.server.model.reminder.EventStatus.TRIGGERED, System.currentTimeMillis())
    }

    fun markEventAsSkipped(id: String): Boolean {
        return updateEventStatus(id, com.example.mcp.server.model.reminder.EventStatus.SKIPPED, System.currentTimeMillis())
    }

    fun getRemindersCount(): Int {
        return reminderDao.count()
    }

    fun getReminderEventsCount(): Int {
        return reminderEventDao.count()
    }

    fun createEventForReminder(
        reminder: Reminder,
        scheduledTime: Long,
        context: com.example.mcp.server.model.reminder.ReminderContext?
    ): ReminderEvent? {
        val event = ReminderEvent(
            id = ReminderEvent.generateId(),
            reminderId = reminder.id,
            type = reminder.type,
            scheduledTime = scheduledTime,
            triggeredAt = null,
            status = com.example.mcp.server.model.reminder.EventStatus.PENDING,
            context = context,
            response = null
        )

        return if (addReminderEvent(event)) event else null
    }
}