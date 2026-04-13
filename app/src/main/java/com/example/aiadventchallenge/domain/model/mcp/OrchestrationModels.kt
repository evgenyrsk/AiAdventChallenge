package com.example.aiadventchallenge.domain.model.mcp

data class FitnessIntent(
    val needsNutritionMetrics: Boolean = false,
    val needsMealGuidance: Boolean = false,
    val needsTrainingGuidance: Boolean = false,
    val extractedParams: Map<String, Any?> = emptyMap()
)

data class UserProfileContext(
    val sex: String? = null,
    val age: Int? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val activityLevel: String? = null,
    val goal: String? = null,
    val trainingLevel: String? = null,
    val trainingDaysPerWeek: Int? = null,
    val mealsPerDay: Int? = null
)

data class ToolCall(
    val tool: String,
    val params: Map<String, Any?>,
    val dependsOn: String? = null
)

data class ToolExecutionResult(
    val tool: String,
    val serverId: String,
    val success: Boolean,
    val durationMs: Long,
    val result: Any?,
    val error: String? = null
)
