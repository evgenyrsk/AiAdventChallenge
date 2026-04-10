package com.example.mcp.server.pipeline.usecases

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.dto.fitness_export.FitnessSummaryExportResult
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineExecutor
import com.example.mcp.server.pipeline.PipelineResult
import com.example.mcp.server.pipeline.steps.SaveSummaryToFileStep
import com.example.mcp.server.pipeline.steps.SaveSummaryToFileStepOutput
import com.example.mcp.server.pipeline.steps.SearchFitnessLogsStep
import com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepInput
import com.example.mcp.server.pipeline.steps.SearchFitnessLogsStepOutput
import com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStep
import com.example.mcp.server.pipeline.steps.SummarizeFitnessLogsStepOutput
import com.example.mcp.server.service.file_export.SummaryFileExportService
import java.util.UUID

class FitnessSummaryExportPipeline(
    private val repository: FitnessReminderRepository,
    private val fileExportService: SummaryFileExportService,
    private val executor: PipelineExecutor = PipelineExecutor.create()
) {

    suspend fun execute(
        period: String = "last_7_days",
        days: Int = 7,
        format: String = "json"
    ): PipelineResult<FitnessSummaryExportResult> {
        val pipelineId = "fitness_summary_export_${UUID.randomUUID()}"
        val context = PipelineContext.create(pipelineId, "Fitness Summary Export Pipeline")

        val steps = listOf(
            SearchFitnessLogsStep(repository) as com.example.mcp.server.pipeline.PipelineStep<*, *>,
            SummarizeFitnessLogsStep() as com.example.mcp.server.pipeline.PipelineStep<*, *>,
            SaveSummaryToFileStep(fileExportService, format) as com.example.mcp.server.pipeline.PipelineStep<*, *>
        )

        val initialInput = SearchFitnessLogsStepInput(
            period = period,
            days = days
        )

        println("📊 About to execute pipeline with ${steps.size} steps")
        val result: PipelineResult<*> = executor.execute(
            steps,
            initialInput,
            context
        )
        println("📊 Pipeline executed, result type: ${result::class.simpleName}")

        return when (result) {
            is PipelineResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val saveOutput = result.data as SaveSummaryToFileStepOutput
                println("📊 Creating FitnessSummaryExportResult with filePath: ${saveOutput.filePath}")

                PipelineResult.Success(
                    stepName = "fitness_summary_export_complete",
                    data = FitnessSummaryExportResult(
                        success = true,
                        filePath = saveOutput.filePath,
                        format = saveOutput.format,
                        savedAt = saveOutput.savedAt,
                        errorMessage = null
                    )
                )
            }
            is PipelineResult.Failure -> {
                println("📊 Pipeline failed: ${result.errorMessage}")
                PipelineResult.Success(
                    stepName = "fitness_summary_export_complete",
                    data = FitnessSummaryExportResult(
                        success = false,
                        filePath = null,
                        format = null,
                        savedAt = null,
                        errorMessage = result.errorMessage
                    )
                )
            }
        }
    }

    suspend fun executeWithFullOutput(
        period: String = "last_7_days",
        days: Int = 7,
        format: String = "json"
    ): Pair<PipelineResult<FitnessSummaryExportResult>, FitnessSummaryExportFullData?> {
        val pipelineId = "fitness_summary_export_full_${UUID.randomUUID()}"
        val context = PipelineContext.create(pipelineId, "Fitness Summary Export Pipeline")

        val steps = listOf(
            SearchFitnessLogsStep(repository) as com.example.mcp.server.pipeline.PipelineStep<*, *>,
            SummarizeFitnessLogsStep() as com.example.mcp.server.pipeline.PipelineStep<*, *>,
            SaveSummaryToFileStep(fileExportService, format) as com.example.mcp.server.pipeline.PipelineStep<*, *>
        )

        val initialInput = SearchFitnessLogsStepInput(
            period = period,
            days = days
        )

        val result: PipelineResult<*> = executor.execute(
            steps,
            initialInput,
            context
        )

        if (result is PipelineResult.Success) {
            val searchOutput = context.getData<SearchFitnessLogsStepOutput>("search_fitness_logs")
            val summarizeOutput = context.getData<SummarizeFitnessLogsStepOutput>("summarize_fitness_logs")
            val saveOutput = context.getData<SaveSummaryToFileStepOutput>("save_summary_to_file")

            if (searchOutput != null && summarizeOutput != null && saveOutput != null) {
                val fullData = FitnessSummaryExportFullData(
                    searchResult = searchOutput,
                    summaryResult = summarizeOutput,
                    saveResult = saveOutput,
                    exportResult = FitnessSummaryExportResult(
                        success = true,
                        filePath = saveOutput.filePath,
                        format = saveOutput.format,
                        savedAt = saveOutput.savedAt,
                        errorMessage = null
                    )
                )

                val successResult = PipelineResult.Success(
                    stepName = "fitness_summary_export_complete",
                    data = fullData.exportResult
                )
                return successResult to fullData
            }
        }

        if (result is PipelineResult.Failure) {
            return PipelineResult.Success(
                stepName = "fitness_summary_export_complete",
                data = FitnessSummaryExportResult(
                    success = false,
                    filePath = null,
                    format = null,
                    savedAt = null,
                    errorMessage = result.errorMessage
                )
            ) to null
        }

        return (result as PipelineResult.Success<*>).let { successResult ->
            PipelineResult.Success(
                stepName = "fitness_summary_export_complete",
                data = FitnessSummaryExportResult(
                    success = true,
                    filePath = null,
                    format = null,
                    savedAt = System.currentTimeMillis(),
                    errorMessage = null
                )
            ) to null
        }
    }
}

@kotlinx.serialization.Serializable
data class FitnessSummaryExportFullData(
    val searchResult: SearchFitnessLogsStepOutput?,
    val summaryResult: SummarizeFitnessLogsStepOutput?,
    val saveResult: SaveSummaryToFileStepOutput?,
    val exportResult: FitnessSummaryExportResult
)