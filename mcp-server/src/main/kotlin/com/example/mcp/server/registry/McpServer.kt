package com.example.mcp.server.registry

import kotlinx.serialization.Serializable

@Serializable
data class McpServer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val status: ServerStatus = ServerStatus.STARTING,
    val tools: List<String> = emptyList(),
    val registeredAt: Long = System.currentTimeMillis(),
    val lastHealthCheck: Long? = null
) {
    val baseUrl: String
        get() = "http://$host:$port"
    
    val healthCheckUrl: String
        get() = "$baseUrl/health"
    
    companion object {
        fun nutritionMetricsServer(port: Int = 8081) = McpServer(
            id = "nutrition-metrics-server-1",
            name = "Nutrition Metrics MCP Server",
            host = "localhost",
            port = port,
            tools = listOf(
                "ping",
                "get_app_info",
                "calculate_nutrition_metrics"
            )
        )
        
        fun mealGuidanceServer(port: Int = 8082) = McpServer(
            id = "meal-guidance-server-1",
            name = "Meal Guidance MCP Server",
            host = "localhost",
            port = port,
            tools = listOf(
                "ping",
                "get_app_info",
                "generate_meal_guidance"
            )
        )
        
        fun trainingGuidanceServer(port: Int = 8083) = McpServer(
            id = "training-guidance-server-1",
            name = "Training Guidance MCP Server",
            host = "localhost",
            port = port,
            tools = listOf(
                "ping",
                "get_app_info",
                "generate_training_guidance"
            )
        )
    }
}

@Serializable
enum class ServerStatus {
    STARTING,
    HEALTHY,
    DEGRADED,
    OFFLINE
}
