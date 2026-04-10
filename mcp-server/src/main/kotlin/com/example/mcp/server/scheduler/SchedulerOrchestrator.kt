package com.example.mcp.server.scheduler

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.model.reminder.ReminderEvent
import com.example.mcp.server.pipeline.usecases.DailyReminderPipeline
import com.example.mcp.server.pipeline.usecases.WeeklySummaryPipeline
import com.example.mcp.server.pipeline.usecases.FitnessSummaryExportPipeline
import com.example.mcp.server.service.fitness.FitnessSummaryService
import com.example.mcp.server.service.reminder.ReminderAnalysisService
import com.example.mcp.server.service.reminder.ReminderService
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SchedulerOrchestrator(
    private val repository: FitnessReminderRepository,
    private val reminderService: ReminderService,
    private val analysisService: ReminderAnalysisService,
    private val summaryService: FitnessSummaryService,
    private val dailyReminderIntervalMinutes: Int = 60,
    private val weeklySummaryIntervalMinutes: Int = 1440
) {

    private val dailyReminderPipeline = DailyReminderPipeline(reminderService, analysisService)
    private val weeklySummaryPipeline = WeeklySummaryPipeline(repository, summaryService)
    private val fitnessSummaryExportPipeline = FitnessSummaryExportPipeline(
        repository = repository,
        fileExportService = com.example.mcp.server.service.file_export.SummaryFileExportService(exportDirectory = "/tmp")
    )

    private val dailyReminderScheduler = DailyReminderScheduler(
        dailyReminderPipeline,
        dailyReminderIntervalMinutes
    )

    private val weeklySummaryScheduler = BackgroundSummaryScheduler(
        repository,
        summaryService,
        weeklySummaryIntervalMinutes
    )

    private val fitnessSummaryExportScheduler = FitnessSummaryExportScheduler(
        fitnessSummaryExportPipeline,
        1440  // раз в сутки
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startAll() {
        println("🚀 Starting all schedulers...")

        dailyReminderScheduler.start(scope)
        weeklySummaryScheduler.start(scope)
        fitnessSummaryExportScheduler.start(scope)

        println("✅ All schedulers started")
    }

    fun stopAll() {
        println("🛑 Stopping all schedulers...")
        
        dailyReminderScheduler.stop()
        weeklySummaryScheduler.stop()
        fitnessSummaryExportScheduler.stop()
        
        println("✅ All schedulers stopped")
    }

    fun runDailyReminderNow(): Map<String, Any> {
        return mapOf(
            "success" to true,
            "message" to "Daily reminder job started. Check status with get_job_status."
        )
    }

    fun runWeeklySummaryNow(): ScheduledSummary? {
        val (result, summary) = runBlocking {
            weeklySummaryPipeline.executeWeeklySummaryWithOutput()
        }

        return when {
            result is com.example.mcp.server.pipeline.PipelineResult.Success && summary != null -> {
                summary
            }
            else -> null
        }
    }

    suspend fun runFitnessSummaryExportNow(): Pair<Boolean, String?> {
        val result = fitnessSummaryExportScheduler.runNow()

        return when (result) {
            is com.example.mcp.server.pipeline.PipelineResult.Success -> {
                true to result.data.filePath
            }
            is com.example.mcp.server.pipeline.PipelineResult.Failure -> {
                false to result.errorMessage
            }
        }
    }

    fun runAllNow(): Map<String, Any> {
        val dailyResult = runDailyReminderNow()
        val weeklySummary = runWeeklySummaryNow()

        return mapOf(
            "daily_reminders" to dailyResult,
            "weekly_summary" to (weeklySummary != null),
            "weekly_summary_id" to (weeklySummary?.toEntity()?.id ?: "")
        )
    }

    fun getAllStatuses(): Map<String, String> {
        return mapOf(
            "daily_reminder_scheduler" to dailyReminderScheduler.getStatus(),
            "weekly_summary_scheduler" to weeklySummaryScheduler.getStatus(),
            "fitness_summary_export_scheduler" to fitnessSummaryExportScheduler.getStatus()
        )
    }

    fun getJobStatus(jobId: String): Map<String, Any> {
        return when (jobId) {
            "daily_reminders" -> mapOf(
                "job_id" to "daily_reminders",
                "status" to dailyReminderScheduler.getStatus(),
                "interval_minutes" to dailyReminderIntervalMinutes,
                "description" to "Daily reminder check and notification"
            )
            "weekly_summary" -> mapOf(
                "job_id" to "weekly_summary",
                "status" to weeklySummaryScheduler.getStatus(),
                "interval_minutes" to weeklySummaryIntervalMinutes,
                "description" to "Weekly fitness summary generation"
            )
            "fitness_summary_export" -> mapOf(
                "job_id" to "fitness_summary_export",
                "status" to fitnessSummaryExportScheduler.getStatus(),
                "interval_minutes" to 1440,
                "description" to "Daily fitness summary export to file"
            )
            else -> mapOf(
                "job_id" to jobId,
                "status" to "not_found",
                "error" to "Job not found"
            )
        }
    }

    fun listAvailableJobs(): List<Map<String, Any>> {
        return listOf(
            mapOf(
                "job_id" to "daily_reminders",
                "name" to "Daily Reminders",
                "description" to "Check and send daily workout/hydration/protein/sleep reminders",
                "interval_minutes" to dailyReminderIntervalMinutes,
                "status" to dailyReminderScheduler.getStatus()
            ),
            mapOf(
                "job_id" to "weekly_summary",
                "name" to "Weekly Summary",
                "description" to "Generate weekly fitness summary with metrics and insights",
                "interval_minutes" to weeklySummaryIntervalMinutes,
                "status" to weeklySummaryScheduler.getStatus()
            ),
            mapOf(
                "job_id" to "fitness_summary_export",
                "name" to "Fitness Summary Export",
                "description" to "Daily fitness summary export to file (search → summarize → save)",
                "interval_minutes" to 1440,
                "status" to fitnessSummaryExportScheduler.getStatus()
            )
        )
    }

    fun shutdown() {
        stopAll()
        scope.cancel()
    }
}