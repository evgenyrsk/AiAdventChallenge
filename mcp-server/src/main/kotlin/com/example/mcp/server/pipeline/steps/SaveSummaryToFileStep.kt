package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.dto.fitness_export.FitnessSummaryExportData
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import com.example.mcp.server.service.file_export.SummaryFileExportService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveSummaryToFileStep(
    private val fileExportService: SummaryFileExportService,
    private val format: String = "json"
) : AbstractPipelineStep<SummarizeFitnessLogsStepOutput, SaveSummaryToFileStepOutput>(
    stepName = "save_summary_to_file",
    description = "Save summary to file"
) {

    override suspend fun doExecute(
        input: SummarizeFitnessLogsStepOutput,
        context: PipelineContext
    ): PipelineResult<SaveSummaryToFileStepOutput> {
        fileExportService.ensureDirectoryExists()

        val filename = fileExportService.generateFilename(
            input.period,
            format
        )

        val result = when (format.lowercase()) {
            "txt" -> {
                val metadata = listOf(
                    "Period" to input.period,
                    "Entries" to input.entriesCount,
                    "Workouts" to input.workoutsCompleted,
                    "Avg Weight" to (input.avgWeight?.let { "%.1f kg".format(it) } ?: "N/A"),
                    "Avg Steps" to (input.avgSteps?.let { "%,d".format(it) } ?: "N/A"),
                    "Avg Sleep" to (input.avgSleepHours?.let { "%.1f h".format(it) } ?: "N/A"),
                    "Avg Protein" to (input.avgProtein?.let { "%d g".format(it) } ?: "N/A")
                ).toMap()
                fileExportService.exportToTxt(input.summaryText, metadata, filename)
            }
            else -> {
                val summaryExportData = FitnessSummaryExportData(
                    period = input.period,
                    entriesCount = input.entriesCount,
                    avgWeight = input.avgWeight,
                    workoutsCompleted = input.workoutsCompleted,
                    avgSteps = input.avgSteps,
                    avgSleepHours = input.avgSleepHours,
                    avgProtein = input.avgProtein,
                    summaryText = input.summaryText,
                    exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                )
                fileExportService.exportToJson(summaryExportData, filename)
            }
        }

        if (result.success) {
            return PipelineResult.Success(
                stepName = stepName,
                data = SaveSummaryToFileStepOutput(
                    filePath = result.filePath ?: "",
                    format = result.format,
                    savedAt = System.currentTimeMillis()
                )
            )
        } else {
            return PipelineResult.Failure(
                stepName = stepName,
                errorMessage = result.error ?: "Unknown error while saving file"
            )
        }
    }
}
