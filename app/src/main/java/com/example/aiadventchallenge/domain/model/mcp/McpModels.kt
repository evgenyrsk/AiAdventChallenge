package com.example.aiadventchallenge.domain.model.mcp

data class McpTool(
    val name: String,
    val description: String
)

data class McpConnectionResult(
    val isConnected: Boolean,
    val tools: List<McpTool>,
    val error: String? = null
)

enum class McpConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class CalculateNutritionParams(
    val sex: String,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val activityLevel: String,
    val goal: String
)

data class CalculateNutritionResult(
    val calories: Int,
    val proteinGrams: Int,
    val fatGrams: Int,
    val carbsGrams: Int,
    val explanation: String
)
