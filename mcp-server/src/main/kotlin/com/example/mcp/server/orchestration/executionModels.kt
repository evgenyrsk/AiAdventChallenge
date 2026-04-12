package com.example.mcp.server.orchestration

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ExecutionStepResult(
    val stepId: String,
    val serverId: String,
    val toolName: String,
    val status: String,
    val durationMs: Long,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class CrossServerFlowResult(
    val success: Boolean,
    val flowName: String,
    val flowId: String,
    val stepsExecuted: Int,
    val totalSteps: Int,
    val finalResult: JsonObject? = null,
    val executionSteps: List<ExecutionStepResult>,
    val durationMs: Long,
    val errorMessage: String? = null
)
