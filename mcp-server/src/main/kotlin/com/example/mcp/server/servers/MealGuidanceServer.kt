package com.example.mcp.server.servers

import com.example.mcp.server.handler.AbstractMcpJsonRpcHandler
import com.example.mcp.server.model.Tool

class MealGuidanceServer(
    port: Int = 8082
) : McpServer(
    port = port,
    serverName = "Meal Guidance Server"
) {
    override val handler: AbstractMcpJsonRpcHandler by lazy {
        MealGuidanceHandler()
    }
}

class MealGuidanceHandler : AbstractMcpJsonRpcHandler() {
    override val tools: List<Tool> = listOf(
        Tool(
            name = "ping",
            description = "Simple ping tool to test MCP connection. Returns 'pong' message."
        ),
        Tool(
            name = "get_app_info",
            description = "Returns information about application including version, platform, and build details."
        ),
        Tool(
            name = "generate_meal_guidance",
            description = "Generates meal guidance based on nutrition metrics. Parameters: goal, targetCalories, proteinG, fatG, carbsG, mealsPerDay (optional, default 3), dietaryPreferences (optional), dietaryRestrictions (optional, default none). Returns mealStrategy, mealDistribution, recommendedFoods, foodsToLimit, notes."
        )
    )

    override fun getServerInfo(): String = "Meal Guidance MCP Server"
}
