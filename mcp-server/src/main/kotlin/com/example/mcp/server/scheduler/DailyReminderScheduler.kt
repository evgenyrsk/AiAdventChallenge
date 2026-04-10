package com.example.mcp.server.scheduler

import com.example.mcp.server.model.reminder.ReminderEvent
import com.example.mcp.server.pipeline.usecases.DailyReminderPipeline
import com.example.mcp.server.pipeline.usecases.DailyReminderResult
import kotlinx.coroutines.*

class DailyReminderScheduler(
    private val dailyReminderPipeline: DailyReminderPipeline,
    private val intervalMinutes: Int = 60
) {

    private var schedulerJob: Job? = null
    private var isRunning = false

    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("⚠️  Daily Reminder Scheduler is already running")
            return
        }

        isRunning = true
        println("📅 Starting DailyReminderScheduler (interval: ${intervalMinutes}min)")

        schedulerJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    runDailyReminderCheck()
                } catch (e: Exception) {
                    println("❌ Error in daily reminder scheduler: ${e.message}")
                    e.printStackTrace()
                }

                delay(intervalMinutes * 60 * 1000L)
            }
        }
    }

    fun stop() {
        if (!isRunning) {
            println("⚠️  Daily Reminder Scheduler is not running")
            return
        }

        isRunning = false
        schedulerJob?.cancel()
        println("⏹️  DailyReminderScheduler stopped")
    }

    suspend fun runDailyReminderCheckNow(): DailyReminderResult {
        println("🔄 Running daily reminder check manually")
        return runDailyReminderCheck()
    }

    private suspend fun runDailyReminderCheck(): DailyReminderResult {
        return try {
            val result = dailyReminderPipeline.executeDailyReminders()

            if (result is com.example.mcp.server.pipeline.PipelineResult.Success) {
                val data = result.data
                println("✅ Daily reminder check completed: ${data.createdEvents.size} events created, ${data.skippedEvents} skipped")
                
                data.createdEvents.forEach { event ->
                    println("   📧 Created reminder: ${event.type} - ${event.context?.workoutsToday} workouts today")
                }

                data
            } else if (result is com.example.mcp.server.pipeline.PipelineResult.Failure) {
                println("❌ Daily reminder check failed: ${result.errorMessage}")
                DailyReminderResult(
                    success = false,
                    createdEvents = emptyList(),
                    skippedEvents = 0,
                    errorMessage = result.errorMessage
                )
            } else {
                DailyReminderResult(
                    success = false,
                    createdEvents = emptyList(),
                    skippedEvents = 0,
                    errorMessage = "Unknown error"
                )
            }
        } catch (e: Exception) {
            println("❌ Error running daily reminder check: ${e.message}")
            e.printStackTrace()
            DailyReminderResult(
                success = false,
                createdEvents = emptyList(),
                skippedEvents = 0,
                errorMessage = e.message ?: "Unknown error"
            )
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