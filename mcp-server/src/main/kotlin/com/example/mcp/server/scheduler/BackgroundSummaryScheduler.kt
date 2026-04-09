package com.example.mcp.server.scheduler

import com.example.mcp.server.data.fitness.FitnessRepository
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.service.fitness.FitnessSummaryService
import kotlinx.coroutines.*

class BackgroundSummaryScheduler(
    private val repository: FitnessRepository,
    private val summaryService: FitnessSummaryService,
    private val intervalMinutes: Int = 1
) {

    private var schedulerJob: Job? = null
    private var isRunning = false

    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("⚠️  Scheduler is already running")
            return
        }

        isRunning = true
        println("📅 Starting BackgroundSummaryScheduler (interval: ${intervalMinutes}min)")

        schedulerJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    runScheduledSummary()
                } catch (e: Exception) {
                    println("❌ Error in scheduler job: ${e.message}")
                    e.printStackTrace()
                }

                delay(intervalMinutes * 60 * 1000L)
            }
        }
    }

    fun stop() {
        if (!isRunning) {
            println("⚠️  Scheduler is not running")
            return
        }

        isRunning = false
        schedulerJob?.cancel()
        println("⏹️  BackgroundSummaryScheduler stopped")
    }

    fun runScheduledSummaryNow(): ScheduledSummary? {
        println("🔄 Running scheduled summary manually")
        return runScheduledSummary()
    }

    private fun runScheduledSummary(): ScheduledSummary? {
        return try {
            val logs = repository.getLastNDaysFitnessLogs(7)

            if (logs.isEmpty()) {
                println("ℹ️  No fitness logs found, skipping summary generation")
                return null
            }

            val summary = summaryService.generateSummary(logs, "last_7_days")

            val scheduledSummary = ScheduledSummary(
                period = summary.period,
                entriesCount = summary.entriesCount,
                avgWeight = summary.avgWeight,
                workoutsCompleted = summary.workoutsCompleted,
                avgSteps = summary.avgSteps,
                avgSleepHours = summary.avgSleepHours,
                avgProtein = summary.avgProtein,
                adherenceScore = summary.adherenceScore,
                summaryText = summary.summaryText,
                createdAt = System.currentTimeMillis()
            )

            val success = repository.addScheduledSummary(scheduledSummary)

            if (success) {
                println("✅ Summary generated and saved: ${summary.entriesCount} entries")
                println("   Summary: ${summary.summaryText}")
                scheduledSummary
            } else {
                println("❌ Failed to save summary")
                null
            }
        } catch (e: Exception) {
            println("❌ Error generating scheduled summary: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    fun getStatus(): String {
        return if (isRunning) {
            "Running (interval: ${intervalMinutes}min)"
        } else {
            "Stopped"
        }
    }
}