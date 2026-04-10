package com.example.mcp.server.scheduler

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.pipeline.usecases.WeeklySummaryPipeline
import com.example.mcp.server.service.fitness.FitnessSummaryService
import kotlinx.coroutines.*

class BackgroundSummaryScheduler(
    private val repository: FitnessReminderRepository,
    private val summaryService: FitnessSummaryService,
    private val intervalMinutes: Int = 1440
) {

    private val weeklySummaryPipeline = WeeklySummaryPipeline(repository, summaryService)
    
    private var schedulerJob: Job? = null
    private var isRunning = false

    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("⚠️  BackgroundSummaryScheduler is already running")
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
            println("⚠️  BackgroundSummaryScheduler is not running")
            return
        }

        isRunning = false
        schedulerJob?.cancel()
        println("⏹️  BackgroundSummaryScheduler stopped")
    }

    suspend fun runScheduledSummaryNow(): ScheduledSummary? {
        println("🔄 Running scheduled summary manually")
        return runScheduledSummary()
    }

    private suspend fun runScheduledSummary(): ScheduledSummary? {
        return try {
            val (result, summary) = weeklySummaryPipeline.executeWeeklySummaryWithOutput()

            if (result is com.example.mcp.server.pipeline.PipelineResult.Success && summary != null) {
                println("✅ Summary generated and saved: ${summary.entriesCount} entries")
                println("   Summary: ${summary.summaryText}")
                summary
            } else if (result is com.example.mcp.server.pipeline.PipelineResult.Failure) {
                println("❌ Failed to generate summary: ${result.errorMessage}")
                null
            } else {
                println("ℹ️  No fitness logs found, skipping summary generation")
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