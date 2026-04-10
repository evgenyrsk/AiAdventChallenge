package com.example.mcp.server.scheduler

import com.example.mcp.server.pipeline.usecases.FitnessSummaryExportPipeline
import kotlinx.coroutines.*

class FitnessSummaryExportScheduler(
    private val pipeline: FitnessSummaryExportPipeline,
    private val intervalMinutes: Int = 1440
) {
    private var job: Job? = null
    private var isRunning = false

    fun start(scope: CoroutineScope) {
        if (isRunning) {
            println("⚠️  Fitness Summary Export Scheduler is already running")
            return
        }

        isRunning = true
        job = scope.launch {
            println("🚀 Fitness Summary Export Scheduler started (interval: ${intervalMinutes}min)")
            
            while (isActive) {
                try {
                    println("🔍 Starting Fitness Summary Export Pipeline...")
                    val result = pipeline.execute(
                        period = "last_7_days",
                        days = 7,
                        format = "json"
                    )

                    when (result) {
                        is com.example.mcp.server.pipeline.PipelineResult.Success -> {
                            println("✅ Fitness summary export completed: ${result.data?.filePath}")
                        }
                        is com.example.mcp.server.pipeline.PipelineResult.Failure -> {
                            println("❌ Fitness summary export failed: ${result.errorMessage}")
                        }
                    }
                } catch (e: Exception) {
                    println("❌ Error in Fitness Summary Export Scheduler: ${e.message}")
                    e.printStackTrace()
                }

                delay(intervalMinutes * 60 * 1000L)
            }
        }
    }

    fun stop() {
        if (!isRunning) {
            println("⚠️  Fitness Summary Export Scheduler is not running")
            return
        }

        isRunning = false
        job?.cancel()
        println("🛑 Fitness Summary Export Scheduler stopped")
    }

    fun getStatus(): String {
        return when {
            isRunning -> "running (interval: ${intervalMinutes}min)"
            else -> "stopped"
        }
    }

    suspend fun runNow(): com.example.mcp.server.pipeline.PipelineResult<com.example.mcp.server.dto.fitness_export.FitnessSummaryExportResult> {
        return pipeline.execute(
            period = "last_7_days",
            days = 7,
            format = "json"
        )
    }
}