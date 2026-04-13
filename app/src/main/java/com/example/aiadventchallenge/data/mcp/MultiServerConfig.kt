package com.example.aiadventchallenge.data.mcp

data class McpServerConfig(
    val serverId: String,
    val name: String,
    val baseUrl: String,
    val availableTools: List<String>
) {
    companion object {
        fun nutritionMetricsServer(port: Int = 8081) = McpServerConfig(
            serverId = "nutrition-metrics-server-1",
            name = "Nutrition Metrics",
            baseUrl = "http://10.0.2.2:$port",
            availableTools = listOf("calculate_nutrition_metrics")
        )
        
        fun mealGuidanceServer(port: Int = 8082) = McpServerConfig(
            serverId = "meal-guidance-server-1",
            name = "Meal Guidance",
            baseUrl = "http://10.0.2.2:$port",
            availableTools = listOf("generate_meal_guidance")
        )
        
        fun trainingGuidanceServer(port: Int = 8083) = McpServerConfig(
            serverId = "training-guidance-server-1",
            name = "Training Guidance",
            baseUrl = "http://10.0.2.2:$port",
            availableTools = listOf("generate_training_guidance")
        )
        
        fun getAllServers() = listOf(
            nutritionMetricsServer(),
            mealGuidanceServer(),
            trainingGuidanceServer()
        )
    }
}
