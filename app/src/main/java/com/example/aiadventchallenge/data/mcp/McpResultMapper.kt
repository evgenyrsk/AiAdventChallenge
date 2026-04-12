package com.example.aiadventchallenge.data.mcp

import com.example.aiadventchallenge.data.mcp.model.AddFitnessLogResult
import com.example.aiadventchallenge.data.mcp.model.FitnessSummaryExportFullResponse
import com.example.aiadventchallenge.data.mcp.model.FitnessSummaryResult
import com.example.aiadventchallenge.data.mcp.model.JsonRpcResult
import com.example.aiadventchallenge.data.mcp.model.RunScheduledSummaryResult
import com.example.aiadventchallenge.data.mcp.model.ScheduledSummaryResult
import com.example.aiadventchallenge.domain.mcp.McpToolData

internal fun JsonRpcResult.toMcpToolData(): McpToolData = when {
    flowResult != null -> {
        val flow = flowResult!!
        McpToolData.MultiServerFlow(
            com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult(
                success = flow.success,
                flowName = flow.flowName ?: "",
                flowId = flow.flowId ?: "",
                stepsExecuted = flow.stepsExecuted ?: 0,
                totalSteps = flow.totalSteps ?: 0,
                durationMs = flow.durationMs ?: 0,
                errorMessage = flow.errorMessage,
                finalResult = flow.finalResult,
                executionSteps = flow.executionSteps?.map { step ->
                    com.example.aiadventchallenge.domain.model.mcp.ExecutionStepResult(
                        stepId = step.stepId ?: "",
                        serverId = step.serverId ?: "",
                        toolName = step.toolName ?: "",
                        status = step.status ?: "UNKNOWN",
                        durationMs = step.durationMs ?: 0,
                        output = step.output,
                        error = step.error
                    )
                } ?: emptyList()
            )
        )
    }
    fitnessSummaryResult != null -> mapFitnessSummary(fitnessSummaryResult!!)
    scheduledSummaryResult != null -> mapScheduledSummary(scheduledSummaryResult!!)
    addFitnessLogResult != null -> mapAddFitnessLog(addFitnessLogResult!!)
    runScheduledSummaryResult != null -> mapRunScheduledSummary(runScheduledSummaryResult!!)
    fitnessSummaryExportFullResponse != null -> mapExport(fitnessSummaryExportFullResponse!!)
    else -> McpToolData.StringResult(message ?: "")
}

private fun mapFitnessSummary(s: FitnessSummaryResult): McpToolData.FitnessSummary =
    McpToolData.FitnessSummary(
        com.example.aiadventchallenge.domain.mcp.FitnessSummaryData(
            period = s.period,
            entriesCount = s.entriesCount,
            avgWeight = s.avgWeight,
            workoutsCompleted = s.workoutsCompleted,
            avgSteps = s.avgSteps,
            avgSleepHours = s.avgSleepHours,
            avgProtein = s.avgProtein,
            adherenceScore = s.adherenceScore,
            summaryText = s.summaryText
        )
    )

private fun mapScheduledSummary(s: ScheduledSummaryResult): McpToolData.ScheduledSummary =
    McpToolData.ScheduledSummary(
        com.example.aiadventchallenge.domain.mcp.ScheduledSummaryData(
            id = s.id,
            period = s.period,
            entriesCount = s.entriesCount,
            avgWeight = s.avgWeight,
            workoutsCompleted = s.workoutsCompleted,
            avgSteps = s.avgSteps,
            avgSleepHours = s.avgSleepHours,
            avgProtein = s.avgProtein,
            adherenceScore = s.adherenceScore,
            summaryText = s.summaryText,
            createdAt = s.createdAt
        )
    )

private fun mapAddFitnessLog(r: AddFitnessLogResult): McpToolData.AddFitnessLog =
    McpToolData.AddFitnessLog(
        com.example.aiadventchallenge.domain.mcp.AddFitnessLogData(
            success = r.success,
            id = r.id,
            message = r.message
        )
    )

private fun mapRunScheduledSummary(r: RunScheduledSummaryResult): McpToolData.RunScheduledSummary =
    McpToolData.RunScheduledSummary(
        com.example.aiadventchallenge.domain.mcp.RunScheduledSummaryData(
            success = r.success,
            summaryId = r.summaryId,
            message = r.message
        )
    )

private fun mapExport(r: FitnessSummaryExportFullResponse): McpToolData.ExportResult {
    val summary = r.summaryData.summaryResult
    return McpToolData.ExportResult(
        com.example.aiadventchallenge.domain.mcp.ExportData(
            filePath = r.exportResult.filePath,
            format = r.exportResult.format,
            savedAt = r.exportResult.savedAt,
            errorMessage = r.exportResult.errorMessage,
            summaryData = summary?.let {
                com.example.aiadventchallenge.domain.mcp.ExportSummaryData(
                    period = it.period,
                    entriesCount = it.entriesCount,
                    avgWeight = it.avgWeight,
                    workoutsCompleted = it.workoutsCompleted,
                    avgSteps = it.avgSteps,
                    avgSleepHours = it.avgSleepHours,
                    avgProtein = it.avgProtein
                )
            }
        )
    )
}
