package com.example.mcp.server.model

import com.example.mcp.server.model.fitness.AddFitnessLogResult
import com.example.mcp.server.model.fitness.FitnessSummaryResult
import com.example.mcp.server.model.fitness.RunScheduledSummaryResult
import com.example.mcp.server.model.fitness.ScheduledSummaryResult
import com.example.mcp.server.model.task.CancelTaskResult
import com.example.mcp.server.model.task.PendingRemindersResult
import com.example.mcp.server.model.task.RunTaskResult
import com.example.mcp.server.model.task.ScheduleTaskResult
import kotlinx.serialization.Serializable
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
    val scheduleTaskResult: ScheduleTaskResult? = null,
    val pendingRemindersResult: PendingRemindersResult? = null,
    val cancelTaskResult: CancelTaskResult? = null,
    val runTaskResult: RunTaskResult? = null
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
