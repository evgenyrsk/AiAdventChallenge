package com.example.mcp.server.dto.pipeline

import com.example.mcp.server.model.fitness.FitnessLogEntity
import com.example.mcp.server.model.fitness.FitnessSummary
import java.time.LocalDate
import java.time.LocalTime

data class LoadLogsInput(
    val days: Int,
    val toDate: LocalDate? = null
)

data class LoadLogsOutput(
    val logs: List<FitnessLogEntity>,
    val period: String,
    val startDate: String,
    val endDate: String
)

data class CalculateSummaryInput(
    val logs: List<FitnessLogEntity>,
    val period: String
)

data class LoadLogsStepOutput(
    val logs: List<FitnessLogEntity>,
    val period: String,
    val startDate: String,
    val endDate: String
)

data class CalculateSummaryOutput(
    val summary: FitnessSummary,
    val metrics: Map<String, Double>
)

data class SaveSummaryInput(
    val summary: FitnessSummary,
    val metrics: Map<String, Double>
)

data class SaveSummaryOutput(
    val saved: Boolean,
    val summaryId: String?,
    val savedAt: Long
)

data class CheckRemindersInput(
    val date: LocalDate,
    val currentTime: LocalTime
)

data class CheckRemindersOutput(
    val dueReminders: List<com.example.mcp.server.model.reminder.Reminder>,
    val context: com.example.mcp.server.model.reminder.ReminderContext
)

data class AnalyzeReminderInput(
    val reminder: com.example.mcp.server.model.reminder.Reminder,
    val context: com.example.mcp.server.model.reminder.ReminderContext
)

data class AnalyzeReminderOutput(
    val shouldTrigger: Boolean,
    val personalizedMessage: String,
    val priority: com.example.mcp.server.service.reminder.ReminderAnalysisService.Priority
)

data class CreateEventInput(
    val reminder: com.example.mcp.server.model.reminder.Reminder,
    val context: com.example.mcp.server.model.reminder.ReminderContext,
    val message: String
)

data class CreateEventOutput(
    val eventId: String?,
    val status: com.example.mcp.server.model.reminder.EventStatus
)