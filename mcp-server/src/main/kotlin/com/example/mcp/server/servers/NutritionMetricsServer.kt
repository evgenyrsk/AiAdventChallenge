package com.example.mcp.server.servers

import com.example.mcp.server.handler.AbstractMcpJsonRpcHandler
import com.example.mcp.server.model.Tool

class NutritionMetricsServer(
    port: Int = 8081
) : McpServer(
    port = port,
    serverName = "Nutrition Metrics Server"
) {
    override val handler: AbstractMcpJsonRpcHandler by lazy {
        NutritionMetricsHandler()
    }
}

class NutritionMetricsHandler : AbstractMcpJsonRpcHandler() {
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
            name = "calculate_nutrition_metrics",
            description = "Calculates BMR, TDEE, target calories and macros. Parameters: sex (male/female), age (years), heightCm (cm), weightKg (kg), activityLevel (sedentary/light/moderate/active/very_active), goal (weight_loss/maintenance/muscle_gain). Returns BMR, TDEE, targetCalories, protein_g, fat_g, carbs_g, notes."
        )
    )

    override fun getServerInfo(): String = "Nutrition Metrics MCP Server"
}
