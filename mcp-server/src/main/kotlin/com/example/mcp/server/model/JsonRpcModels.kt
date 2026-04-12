package com.example.mcp.server.model

import com.example.mcp.server.dto.fitness_export.FitnessExportPipelineData
import com.example.mcp.server.orchestration.CrossServerFlowContext
import com.example.mcp.server.orchestration.CrossServerFlowStep
import com.example.mcp.server.dto.fitness_export.FitnessSummaryExportFullResponse
import com.example.mcp.server.model.fitness.AddFitnessLogResult
import com.example.mcp.server.model.fitness.FitnessSummaryResult
import com.example.mcp.server.model.fitness.RunScheduledSummaryResult
import com.example.mcp.server.model.fitness.ScheduledSummaryResult
import com.example.mcp.server.service.reminder.CreateReminderFromSummaryResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, JsonElement>? = null
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: Int,
    val result: JsonRpcResult? = null,
    val error: JsonRpcError? = null
)

@Serializable
data class JsonRpcResult(
    val tools: List<Tool>? = null,
    val message: String? = null,
    val nutritionResult: CalculateNutritionResult? = null,
    val fitnessSummaryResult: FitnessSummaryResult? = null,
    val scheduledSummaryResult: ScheduledSummaryResult? = null,
    val addFitnessLogResult: AddFitnessLogResult? = null,
    val runScheduledSummaryResult: RunScheduledSummaryResult? = null,
    val createReminderResult: CreateReminderResult? = null,
    val checkRemindersResult: CheckRemindersResult? = null,
    val getActiveRemindersResult: GetActiveRemindersResult? = null,
    val listJobsResult: ListJobsResult? = null,
    val runJobNowResult: RunJobNowResult? = null,
    val getJobStatusResult: GetJobStatusResult? = null,
    val fitnessExportPipelineResult: FitnessExportPipelineData? = null,
    val fitnessSummaryExportFullResponse: FitnessSummaryExportFullResponse? = null,
    val createReminderFromSummaryResult: CreateReminderFromSummaryResult? = null,
    val flowResult: JsonObject? = null,
    val toolResult: JsonObject? = null
)

@Serializable
data class Tool(
    val name: String,
    val description: String
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String
)

@Serializable
data class CalculateNutritionParams(
    val sex: String,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val goal: String
)

@Serializable
data class CalculateNutritionResult(
    val calories: Int,
    val proteinGrams: Int,
    val fatGrams: Int,
    val carbsGrams: Int,
    val explanation: String
)

@Serializable
data class CreateReminderResult(
    val success: Boolean,
    val reminderId: String?,
    val message: String
)

@Serializable
data class CheckRemindersResult(
    val triggered: List<ReminderEventResult>,
    val skipped: List<ReminderEventResult>
)

@Serializable
data class ReminderEventResult(
    val eventId: String,
    val reminderId: String,
    val type: String,
    val title: String,
    val message: String
)

@Serializable
data class GetActiveRemindersResult(
    val reminders: List<ReminderDto>
)

@Serializable
data class ReminderDto(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val time: String,
    val daysOfWeek: List<String>,
    val isActive: Boolean
)

@Serializable
data class ListJobsResult(
    val jobs: List<JobDto>
)

@Serializable
data class JobDto(
    val jobId: String,
    val name: String,
    val description: String,
    val intervalMinutes: Int,
    val status: String
)

@Serializable
data class RunJobNowResult(
    val success: Boolean,
    val jobId: String,
    val resultSummary: String,
    val message: String
)

@Serializable
data class GetJobStatusResult(
    val jobId: String,
    val status: String,
    val intervalMinutes: Int?,
    val description: String?,
    val error: String?
)
