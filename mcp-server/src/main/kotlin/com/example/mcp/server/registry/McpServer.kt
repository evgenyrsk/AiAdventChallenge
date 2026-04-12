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
        fun fitnessServer(port: Int = 8081) = McpServer(
            id = "fitness-server-1",
            name = "Fitness MCP Server",
            host = "localhost",
            port = port,
            tools = listOf(
                "ping",
                "get_app_info",
                "calculate_nutrition_plan",
                "add_fitness_log",
                "get_fitness_summary",
                "run_scheduled_summary",
                "get_latest_scheduled_summary",
                "search_fitness_logs",
                "summarize_fitness_logs",
                "save_summary_to_file",
                "run_fitness_summary_export_pipeline"
            )
        )
        
        fun reminderServer(port: Int = 8082) = McpServer(
            id = "reminder-server-1",
            name = "Reminder MCP Server",
            host = "localhost",
            port = port,
            tools = listOf(
                "create_reminder",
                "check_due_reminders",
                "get_active_reminders",
                "list_available_jobs",
                "run_job_now",
                "get_job_status",
                "create_reminder_from_summary"
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
