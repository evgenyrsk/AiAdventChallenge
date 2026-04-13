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
    data class NutritionMetrics(val result: com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse) : McpToolData()
    data class MealGuidance(val result: com.example.aiadventchallenge.domain.model.mcp.MealGuidanceResponse) : McpToolData()
    data class TrainingGuidance(val result: com.example.aiadventchallenge.domain.model.mcp.TrainingGuidanceResponse) : McpToolData()
}
