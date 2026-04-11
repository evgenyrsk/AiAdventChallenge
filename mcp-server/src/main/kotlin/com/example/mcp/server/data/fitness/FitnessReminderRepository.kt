package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.fitness.FitnessLog
import com.example.mcp.server.model.fitness.FitnessLogEntity
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.model.reminder.Reminder
import com.example.mcp.server.model.reminder.ReminderEvent
import com.example.mcp.server.model.reminder.ReminderType
import java.time.LocalDate
import java.time.LocalTime

class FitnessReminderRepository(
    private val database: ReminderDatabase,
    private val fitnessLogDao: FitnessLogDao,
    private val scheduledSummaryDao: ScheduledSummaryDao,
    private val reminderDao: ReminderDao,
    private val reminderEventDao: ReminderEventDao
) {
    
    private val fitnessRepository = FitnessRepository(fitnessLogDao, scheduledSummaryDao)
    private val reminderRepository = ReminderRepository(reminderDao, reminderEventDao)

    fun addFitnessLog(log: FitnessLog): Boolean = fitnessRepository.addFitnessLog(log)

    fun getAllFitnessLogs(): List<FitnessLogEntity> = fitnessRepository.getAllFitnessLogs()

    fun getFitnessLogsByDateRange(startDate: String, endDate: String): List<FitnessLogEntity> = 
        fitnessRepository.getFitnessLogsByDateRange(startDate, endDate)

    fun getLastNDaysFitnessLogs(days: Int): List<FitnessLogEntity> = 
        fitnessRepository.getLastNDaysFitnessLogs(days)

    fun addScheduledSummary(summary: ScheduledSummary): Boolean = 
        fitnessRepository.addScheduledSummary(summary)

    fun getLatestScheduledSummary(): ScheduledSummary? = 
        fitnessRepository.getLatestScheduledSummary()

    fun getAllScheduledSummaries(): List<ScheduledSummary> = 
        fitnessRepository.getAllScheduledSummaries()

    fun addReminder(reminder: Reminder): Boolean = reminderRepository.addReminder(reminder)

    fun getReminderById(id: String): Reminder? = reminderRepository.getReminderById(id)

    fun getAllActiveReminders(): List<Reminder> = reminderRepository.getAllActiveReminders()

    fun getAllReminders(): List<Reminder> = reminderRepository.getAllReminders()

    fun deleteReminder(id: String): Boolean = reminderRepository.deleteReminder(id)

    fun getRemindersByType(type: ReminderType): List<Reminder> = 
        reminderRepository.getRemindersByType(type)

    fun getRemindersForDayOfWeek(dayOfWeek: java.time.DayOfWeek): List<Reminder> = 
        reminderRepository.getRemindersForDayOfWeek(dayOfWeek)

    fun getRemindersDueAtTime(time: LocalTime): List<Reminder> = 
        reminderRepository.getRemindersDueAtTime(time)

    fun getDueReminders(date: LocalDate, time: LocalTime): List<Reminder> = 
        reminderRepository.getDueReminders(date, time)

    fun addReminderEvent(event: ReminderEvent): Boolean = reminderRepository.addReminderEvent(event)

    fun getReminderEventById(id: String): ReminderEvent? = reminderRepository.getReminderEventById(id)

    fun getEventsByReminderId(reminderId: String, limit: Int = 10): List<ReminderEvent> = 
        reminderRepository.getEventsByReminderId(reminderId, limit)

    fun getPendingEvents(): List<ReminderEvent> = reminderRepository.getPendingEvents()

    fun getEventsInDateRange(startTime: Long, endTime: Long): List<ReminderEvent> = 
        reminderRepository.getEventsInDateRange(startTime, endTime)

    fun updateEventStatus(id: String, status: com.example.mcp.server.model.reminder.EventStatus, triggeredAt: Long?): Boolean = 
        reminderRepository.updateEventStatus(id, status, triggeredAt)

    fun updateEventResponse(id: String, response: String?): Boolean = 
        reminderRepository.updateEventResponse(id, response)

    fun deleteReminderEvent(id: String): Boolean = reminderRepository.deleteReminderEvent(id)

    fun markEventAsTriggered(id: String): Boolean = reminderRepository.markEventAsTriggered(id)

    fun markEventAsSkipped(id: String): Boolean = reminderRepository.markEventAsSkipped(id)

    fun createEventForReminder(
        reminder: Reminder,
        scheduledTime: Long,
        context: com.example.mcp.server.model.reminder.ReminderContext?
    ): ReminderEvent? = reminderRepository.createEventForReminder(reminder, scheduledTime, context)

    fun getFitnessLogsCount(): Int = fitnessRepository.getFitnessLogsCount()

    fun getScheduledSummariesCount(): Int = fitnessRepository.getScheduledSummariesCount()

    fun getRemindersCount(): Int = reminderRepository.getRemindersCount()

    fun getReminderEventsCount(): Int = reminderRepository.getReminderEventsCount()

    fun getDatabase(): ReminderDatabase = database
    
    fun clearAllData(): Boolean {
        println("\n🧹 Clearing all data...")
        
        val logsCleared = fitnessRepository.clearLogs()
        val summariesCleared = fitnessRepository.clearScheduledSummaries()
        val remindersCleared = reminderRepository.clearAll()
        val eventsCleared = reminderRepository.clearAllEvents()
        
        val allCleared = logsCleared && summariesCleared && remindersCleared && eventsCleared
        
        if (allCleared) {
            println("✅ All data cleared successfully")
        } else {
            println("⚠️  Some data clearing failed")
        }
        
        return allCleared
    }
}
