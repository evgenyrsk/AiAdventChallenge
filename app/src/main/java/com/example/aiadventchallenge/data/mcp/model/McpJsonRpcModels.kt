package com.example.aiadventchallenge.data.mcp.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, JsonElement>? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String,
    val id: Int,
    val result: JsonRpcResult? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcResult(
    val tools: List<ToolData>? = null,
    val message: String? = null,
    val addFitnessLogResult: AddFitnessLogResult? = null,
    val fitnessSummaryResult: FitnessSummaryResult? = null,
    val scheduledSummaryResult: ScheduledSummaryResult? = null,
    val runScheduledSummaryResult: RunScheduledSummaryResult? = null,
    val fitnessSummaryExportFullResponse: FitnessSummaryExportFullResponse? = null
)

@Serializable
data class ToolData(
    val name: String,
    val description: String
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

@Serializable
data class AddFitnessLogResult(
    val success: Boolean,
    val id: String,
    val message: String
)

@Serializable
data class FitnessSummaryResult(
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

@Serializable
data class ScheduledSummaryResult(
    val id: String,
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val adherenceScore: Double,
    val summaryText: String,
    val createdAt: String
)

@Serializable
data class RunScheduledSummaryResult(
    val success: Boolean,
    val summaryId: String?,
    val message: String,
    val summary: ScheduledSummaryResult?
)

@Serializable
data class FitnessSummaryExportFullResponse(
    val exportResult: FitnessSummaryExportResult,
    val summaryData: FitnessSummaryExportFullData
)

@Serializable
data class FitnessSummaryExportFullData(
    val searchResult: SearchFitnessLogsStepOutput?,
    val summaryResult: SummarizeFitnessLogsStepOutput?,
    val saveResult: SaveSummaryToFileStepOutput?,
    val exportResult: FitnessSummaryExportResult
)

@Serializable
data class FitnessSummaryExportResult(
    val success: Boolean,
    val filePath: String?,
    val format: String?,
    val savedAt: Long?,
    val errorMessage: String?
)

@Serializable
data class SearchFitnessLogsStepOutput(
    val period: String,
    val entries: List<FitnessLogEntry>?,
    val startDate: String,
    val endDate: String
)

@Serializable
data class SummarizeFitnessLogsStepOutput(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val summaryText: String?
)

@Serializable
data class SaveSummaryToFileStepOutput(
    val filePath: String,
    val format: String,
    val savedAt: Long
)

@Serializable
data class FitnessLogEntry(
    val date: String,
    val weight: Double?,
    val calories: Int?,
    val protein: Int?,
    val workoutCompleted: Boolean?,
    val steps: Int?,
    val sleepHours: Double?,
    val notes: String?
)
