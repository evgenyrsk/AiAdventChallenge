package com.example.mcp.server.dto.adapter

import com.example.mcp.server.dto.fitness_export.*
import com.example.mcp.server.pipeline.steps.*
import com.example.mcp.server.pipeline.usecases.FitnessSummaryExportFullData

object FitnessExportAdapter {

    fun toMcpOutput(stepOutput: SearchFitnessLogsStepOutput): SearchFitnessLogsOutput {
        return SearchFitnessLogsOutput(
            success = true,
            tool = "search_fitness_logs",
            data = FitnessLogsSearchData(
                period = stepOutput.period,
                entries = stepOutput.entries,
                startDate = stepOutput.startDate,
                endDate = stepOutput.endDate
            ),
            error = null
        )
    }

    fun toMcpOutput(stepOutput: SummarizeFitnessLogsStepOutput): SummarizeFitnessLogsOutput {
        return SummarizeFitnessLogsOutput(
            success = true,
            tool = "summarize_fitness_logs",
            data = FitnessSummaryData(
                period = stepOutput.period,
                entriesCount = stepOutput.entriesCount,
                avgWeight = stepOutput.avgWeight,
                workoutsCompleted = stepOutput.workoutsCompleted,
                avgSteps = stepOutput.avgSteps,
                avgSleepHours = stepOutput.avgSleepHours,
                avgProtein = stepOutput.avgProtein,
                summaryText = stepOutput.summaryText
            ),
            error = null
        )
    }

    fun toMcpOutput(stepOutput: SaveSummaryToFileStepOutput): SaveSummaryToFileOutput {
        return SaveSummaryToFileOutput(
            success = true,
            tool = "save_summary_to_file",
            data = FileSaveData(
                filePath = stepOutput.filePath,
                format = stepOutput.format,
                savedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(stepOutput.savedAt))
            ),
            error = null
        )
    }

    fun toMcpPipelineOutput(
        pipelineData: FitnessSummaryExportFullData?
    ): FitnessExportPipelineData {
        if (pipelineData == null) {
            return FitnessExportPipelineData(
                success = false,
                filePath = null,
                format = null,
                savedAt = null
            )
        }

        val search = pipelineData.searchResult
        val summary = pipelineData.summaryResult
        val save = pipelineData.saveResult

        val searchData = if (search != null) {
            FitnessLogsSearchData(
                period = search.period,
                entries = search.entries,
                startDate = search.startDate,
                endDate = search.endDate
            )
        } else {
            null
        }

        val summaryData = if (summary != null) {
            FitnessSummaryData(
                period = summary.period,
                entriesCount = summary.entriesCount,
                avgWeight = summary.avgWeight,
                workoutsCompleted = summary.workoutsCompleted,
                avgSteps = summary.avgSteps,
                avgSleepHours = summary.avgSleepHours,
                avgProtein = summary.avgProtein,
                summaryText = summary.summaryText
            )
        } else {
            null
        }

        return FitnessExportPipelineData(
            success = true,
            filePath = save?.filePath,
            format = save?.format,
            savedAt = save?.savedAt,
            search = searchData,
            summary = summaryData
        )
    }
}