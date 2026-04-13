package com.example.mcp.server.servers

import com.example.mcp.server.handler.AbstractMcpJsonRpcHandler
import com.example.mcp.server.model.Tool

class TrainingGuidanceServer(
    port: Int = 8083
) : McpServer(
    port = port,
    serverName = "Training Guidance Server"
) {
    override val handler: AbstractMcpJsonRpcHandler by lazy {
        TrainingGuidanceHandler()
    }
}

class TrainingGuidanceHandler : AbstractMcpJsonRpcHandler() {
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
            name = "generate_training_guidance",
            description = "Generates training plan. Parameters: goal, trainingLevel (optional, default beginner), trainingDaysPerWeek (optional, default 3), sessionDurationMinutes (optional, default 60), availableEquipment (optional, default gym), restrictions (optional, default none). Returns trainingSplit, weeklyPlan, exercisePrinciples, recoveryNotes, notes."
        )
    )

    override fun getServerInfo(): String = "Training Guidance MCP Server"
}
