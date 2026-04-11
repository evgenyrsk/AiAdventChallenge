package com.example.mcp.server.demo

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.model.fitness.FitnessLog
import com.example.mcp.server.pipeline.usecases.FitnessSummaryExportPipeline
import com.example.mcp.server.service.file_export.SummaryFileExportService
import com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepInput
import com.example.mcp.server.pipeline.steps.SearchFitnessLogsStep
import com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStep
import com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStepInput
import com.example.mcp.server.pipeline.steps.SaveSummaryToFileStep
import com.example.mcp.server.pipeline.steps.SaveSummaryToFileStepInput
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineExecutor
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DemoFitnessSummaryExport(
    private val repository: FitnessReminderRepository
) {

    private val fileExportService = SummaryFileExportService(exportDirectory = "/tmp")
    private val executor = PipelineExecutor.create(enableLogging = true)

    suspend fun demonstrateFullFlow() {
        println("\n" + "=".repeat(60))
        println("DEMO: Fitness Summary Export Pipeline")
        println("=".repeat(60) + "\n")

        println("📝 Step 1: Setup test data")
        setupTestData()

        println("\n📝 Step 2: Run individual tools")
        runIndividualTools()

        println("\n📝 Step 3: Run full pipeline")
        runFullPipeline()

        println("\n📝 Step 4: Verify output files")
        verifyOutputFiles()

        println("\n" + "=".repeat(60))
        println("✅ Demo completed successfully!")
        println("=".repeat(60) + "\n")
    }

    private suspend fun setupTestData() {
        println("\n📊 Adding test fitness logs for last 7 days...")
        
        val today = LocalDate.now()
        val testData = SetupTestData(7).generate7DaysData(today)
        
        var successCount = 0
        testData.forEach { log ->
            val success = repository.addFitnessLog(log)
            if (success) {
                successCount++
                println("   ✅ Added log for ${log.date}: ${log.weight}kg, ${if (log.workoutCompleted) "workout" else "rest"}, ${log.steps} steps")
            } else {
                println("   ❌ Failed to add log for ${log.date}")
            }
        }
        
        println("   📊 Total logs added: $successCount/${testData.size}")
    }


    private suspend fun runIndividualTools() {
        println("\n🔧 Step 2.1: Running search_fitness_logs")

        val context = PipelineContext.create("demo_search", "Demo Search")
        val searchStep = SearchFitnessLogsStep(repository)
        val searchInput = com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepInput(
            period = "last_7_days",
            days = 7
        )

        val searchResult = executor.executeStep(searchStep, searchInput, context)

        if (searchResult is com.example.mcp.server.pipeline.PipelineResult.Success<*>) {
            val output = searchResult.data as com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepOutput
            println("   ✅ Search successful: ${output.entries?.size} entries found")
            output.entries?.take(2)?.forEach { entry ->
                println("      - ${entry.date}: ${entry.weight}kg, ${if (entry.workoutCompleted) "workout" else "rest"}")
            }
        } else {
            println("   ❌ Search failed: ${(searchResult as? com.example.mcp.server.pipeline.PipelineResult.Failure)?.errorMessage}")
            return
        }

        println("\n🔧 Step 2.2: Running summarize_fitness_logs")

        val searchOutput = searchResult.data as com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepOutput
        val summaryStep = com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStep()

        val summaryResult = executor.executeStep(summaryStep, searchOutput, context)

        if (summaryResult is com.example.mcp.server.pipeline.PipelineResult.Success<*>) {
            val sumOutput = summaryResult.data as com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStepOutput
            println("   ✅ Summary successful:")
            println("      - Entries: ${sumOutput.entriesCount}")
            println("      - Workouts: ${sumOutput.workoutsCompleted}")
            println("      - Avg weight: ${sumOutput.avgWeight?.let { "%.1f".format(it) }}kg")
            println("      - Avg steps: ${sumOutput.avgSteps}")
            println("      - Summary: ${sumOutput.summaryText?.take(80)}...")
        } else {
            println("   ❌ Summary failed: ${(summaryResult as? com.example.mcp.server.pipeline.PipelineResult.Failure)?.errorMessage}")
            return
        }

        println("\n🔧 Step 2.3: Running save_summary_to_file (JSON)")

        val sumOutput = summaryResult.data as com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStepOutput
        val saveStep = com.example.mcp.server.pipeline.steps.SaveSummaryToFileStep(fileExportService, "json")

        val saveResult = executor.executeStep(saveStep, sumOutput, context)

        if (saveResult is com.example.mcp.server.pipeline.PipelineResult.Success<*>) {
            val saveOutput = saveResult.data as com.example.mcp.server.pipeline.steps.SaveSummaryToFileStepOutput
            println("   ✅ Save successful:")
            println("      - File: ${saveOutput.filePath}")
            println("      - Format: ${saveOutput.format}")
        } else {
            println("   ❌ Save failed: ${(saveResult as? com.example.mcp.server.pipeline.PipelineResult.Failure)?.errorMessage}")
        }

    }

    private suspend fun runFullPipeline() {
        println("\n🚀 Step 3: Running full pipeline (search → summarize → save)")

        val pipeline = FitnessSummaryExportPipeline(
            repository = repository,
            fileExportService = fileExportService
        )

        val (result, fullData) = pipeline.executeWithFullOutput(
            period = "last_7_days",
            days = 7,
            format = "json"
        )

        if (result is com.example.mcp.server.pipeline.PipelineResult.Success && result.data.success) {
            println("   ✅ Pipeline executed successfully!")
            println("      - File path: ${result.data.filePath}")
            println("      - Format: ${result.data.format}")
            println("      - Saved at: ${result.data.savedAt}")

            if (fullData != null) {
                println("\n   📊 Pipeline execution details:")
                println("      - Search: ${fullData.searchResult?.entries?.size ?: 0} entries")
                println("      - Summary: ${fullData.summaryResult?.workoutsCompleted} workouts")
                println("      - Save: ${fullData.saveResult?.filePath}")
            }
        } else {
            println("   ❌ Pipeline failed: ${result.errorMessage}")
        }
    }

    private fun verifyOutputFiles() {
        println("\n📝 Step 4: Verifying output files")

        val exportDir = File("/tmp")
        val jsonFiles = exportDir.listFiles { file ->
            file.name.startsWith("fitness-summary-") && file.name.endsWith(".json")
        }?.sortedByDescending { it.lastModified() }?.take(3)

        if (jsonFiles != null && jsonFiles.isNotEmpty()) {
            println("   ✅ Found ${jsonFiles.size} recent export files:")
            jsonFiles.forEach { file ->
                val sizeKB = file.length() / 1024
                println("      - ${file.name} (${sizeKB} KB)")
            }

            println("\n   📄 Contents of latest file:")
            val latestFile = jsonFiles.first()
            try {
                val content = latestFile.readText()
                println(content.lines().take(15).joinToString("\n"))
                if (content.lines().size > 15) {
                    println("... (${content.lines().size - 15} more lines)")
                }
            } catch (e: Exception) {
                println("      ❌ Error reading file: ${e.message}")
            }
        } else {
            println("   ⚠️  No export files found in /tmp")
        }
    }
}

suspend fun main() {
    val database = com.example.mcp.server.data.fitness.ReminderDatabase()
    val fitnessLogDao = com.example.mcp.server.data.fitness.FitnessLogDao(database)
    val scheduledSummaryDao = com.example.mcp.server.data.fitness.ScheduledSummaryDao(database)
    val reminderDao = com.example.mcp.server.data.fitness.ReminderDao(database)
    val reminderEventDao = com.example.mcp.server.data.fitness.ReminderEventDao(database)

    val repository = FitnessReminderRepository(
        database,
        fitnessLogDao,
        scheduledSummaryDao,
        reminderDao,
        reminderEventDao
    )

    val demo = DemoFitnessSummaryExport(repository)
    demo.demonstrateFullFlow()
}