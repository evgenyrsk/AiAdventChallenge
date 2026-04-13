package com.example.aiadventchallenge.data.mcp.model

import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse
import com.example.aiadventchallenge.domain.model.mcp.MealGuidanceResponse
import com.example.aiadventchallenge.domain.model.mcp.TrainingGuidanceResponse
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
    val message: String? = null
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
