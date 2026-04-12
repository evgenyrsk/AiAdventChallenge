package com.example.aiadventchallenge.domain.mcp

sealed class McpToolResult {
    data class Success(
        val data: McpToolData
    ) : McpToolResult()
    
    data class Error(
        val message: String
    ) : McpToolResult()
}

sealed class McpToolData {
    data class StringResult(val message: String) : McpToolData()
    data class FitnessSummary(val summary: FitnessSummaryData) : McpToolData()
    data class ScheduledSummary(val summary: ScheduledSummaryData) : McpToolData()
    data class AddFitnessLog(val result: AddFitnessLogData) : McpToolData()
    data class ExportResult(val fullResponse: ExportData) : McpToolData()
    data class RunScheduledSummary(val result: RunScheduledSummaryData) : McpToolData()
    data class MultiServerFlow(val result: com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult) : McpToolData()
}

data class FitnessSummaryData(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val adherenceScore: Double,
    val summaryText: String
)

data class ScheduledSummaryData(
    val id: String,
    val period: String,
    val entriesCount: Int = 0,
    val avgWeight: Double? = null,
    val workoutsCompleted: Int = 0,
    val avgSteps: Int? = null,
    val avgSleepHours: Double? = null,
    val avgProtein: Int? = null,
    val adherenceScore: Double = 0.0,
    val summaryText: String = "",
    val createdAt: String = ""
)

data class AddFitnessLogData(
    val success: Boolean,
    val id: String,
    val message: String
)

data class ExportData(
    val filePath: String?,
    val format: String?,
    val savedAt: Long?,
    val errorMessage: String?,
    val summaryData: ExportSummaryData?
)

data class ExportSummaryData(
    val period: String?,
    val entriesCount: Int?,
    val avgWeight: Double?,
    val workoutsCompleted: Int?,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?
)

data class RunScheduledSummaryData(
    val success: Boolean,
    val summaryId: String?,
    val message: String
)
