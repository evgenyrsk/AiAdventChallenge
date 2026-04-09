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
    val runScheduledSummaryResult: RunScheduledSummaryResult? = null
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
