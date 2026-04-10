package com.example.mcp.server.service.reminder

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.model.fitness.FitnessLogEntity
import com.example.mcp.server.model.reminder.Reminder
import com.example.mcp.server.model.reminder.ReminderContext
import com.example.mcp.server.model.reminder.ReminderEvent
import com.example.mcp.server.model.reminder.ReminderType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ReminderService(
    private val repository: FitnessReminderRepository
) {

    fun createReminder(
        type: ReminderType,
        title: String,
        message: String,
        time: String,
        daysOfWeek: List<com.example.mcp.server.model.reminder.DayOfWeek>
    ): Reminder? {
        val reminder = Reminder(
            id = Reminder.generateId(),
            type = type,
            title = title,
            message = message,
            time = time,
            daysOfWeek = daysOfWeek,
            isActive = true,
            createdAt = System.currentTimeMillis()
        )

        return if (repository.addReminder(reminder)) reminder else null
    }

    fun getActiveReminders(): List<Reminder> {
        return repository.getAllActiveReminders()
    }

    fun getActiveRemindersByType(type: ReminderType): List<Reminder> {
        return repository.getRemindersByType(type)
    }

    fun getDueReminders(date: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now()): List<Reminder> {
        return repository.getDueReminders(date, time)
    }

    fun buildReminderContext(
        date: LocalDate = LocalDate.now()
    ): ReminderContext {
        val logs = repository.getFitnessLogsByDateRange(
            date.toString(),
            date.toString()
        )

        val lastWorkoutDate = getLastWorkoutDate(date)
        val lastSleepDate = getLastSleepDate(date)

        return ReminderContext(
            workoutsToday = logs.count { it.workoutCompleted },
            lastWorkoutDate = lastWorkoutDate,
            caloriesToday = logs.firstOrNull()?.calories,
            proteinToday = logs.firstOrNull()?.protein,
            sleepLastNight = getSleepForDate(lastSleepDate ?: date.toString())
        )
    }

    private fun getLastWorkoutDate(currentDate: LocalDate): String? {
        val logs = repository.getAllFitnessLogs()
        val completedWorkouts = logs.filter { it.workoutCompleted }
        return completedWorkouts.lastOrNull()?.date
    }

    private fun getLastSleepDate(currentDate: LocalDate): String? {
        val logs = repository.getAllFitnessLogs()
        val withSleep = logs.filter { it.sleepHours != null && it.sleepHours!! > 0 }
        return withSleep.lastOrNull()?.date
    }

    private fun getSleepForDate(date: String): Double? {
        val logs = repository.getFitnessLogsByDateRange(date, date)
        return logs.firstOrNull()?.sleepHours
    }

    fun createReminderEvent(
        reminder: Reminder,
        context: ReminderContext,
        personalizedMessage: String? = null
    ): ReminderEvent? {
        val today = LocalDate.now()
        val reminderTime = LocalTime.parse(reminder.time)
        val scheduledTime = LocalDateTime.of(today, reminderTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

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

        return if (repository.addReminderEvent(event)) event else null
    }

    fun triggerReminderEvent(eventId: String): Boolean {
        val event = repository.getReminderEventById(eventId) ?: return false
        return repository.markEventAsTriggered(eventId)
    }

    fun skipReminderEvent(eventId: String): Boolean {
        val event = repository.getReminderEventById(eventId) ?: return false
        return repository.markEventAsSkipped(eventId)
    }

    fun updateReminderEventResponse(eventId: String, response: String): Boolean {
        return repository.updateEventResponse(eventId, response)
    }

    fun getReminderHistory(reminderId: String, limit: Int = 10): List<ReminderEvent> {
        return repository.getEventsByReminderId(reminderId, limit)
    }

    fun getPendingEvents(): List<ReminderEvent> {
        return repository.getPendingEvents()
    }

    fun checkAndCreateReminderEvents(date: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now()): List<ReminderEvent> {
        val dueReminders = getDueReminders(date, time)
        val context = buildReminderContext(date)
        val createdEvents = mutableListOf<ReminderEvent>()

        dueReminders.forEach { reminder ->
            val existingEvents = repository.getEventsInDateRange(
                startOfDay(date).toInstant(ZoneOffset.UTC).toEpochMilli(),
                endOfDay(date).toInstant(ZoneOffset.UTC).toEpochMilli()
            )

            val alreadyExists = existingEvents.any { 
                it.reminderId == reminder.id && 
                it.status == com.example.mcp.server.model.reminder.EventStatus.PENDING
            }

            if (!alreadyExists) {
                val event = createReminderEvent(reminder, context)
                event?.let { createdEvents.add(it) }
            }
        }

        return createdEvents
    }

    private fun startOfDay(date: LocalDate): LocalDateTime {
        return date.atStartOfDay()
    }

    private fun endOfDay(date: LocalDate): LocalDateTime {
        return date.atTime(23, 59, 59)
    }

    fun deactivateReminder(reminderId: String): Boolean {
        val reminder = repository.getReminderById(reminderId) ?: return false
        val deactivatedReminder = reminder.copy(isActive = false)
        return repository.addReminder(deactivatedReminder)
    }

    fun activateReminder(reminderId: String): Boolean {
        val reminder = repository.getReminderById(reminderId) ?: return false
        val activatedReminder = reminder.copy(isActive = true)
        return repository.addReminder(activatedReminder)
    }

    fun deleteReminder(reminderId: String): Boolean {
        return repository.deleteReminder(reminderId)
    }
}