package com.example.aiadventchallenge.domain.model.mcp

import kotlinx.serialization.json.JsonObject

data class MultiServerFlowResult(
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

data class ExecutionStepResult(
    val stepId: String,
    val serverId: String,
    val toolName: String,
    val status: String, // PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    val durationMs: Long,
    val output: String? = null,
    val error: String? = null
)
