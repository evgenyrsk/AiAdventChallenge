package com.example.mcp.server.demo

import com.example.mcp.server.handler.McpJsonRpcHandler
import com.example.mcp.server.orchestration.*
import com.example.mcp.server.registry.McpServer
import com.example.mcp.server.registry.McpServerRegistry
import com.example.mcp.server.tracing.TraceLogger

object MultiMcpDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        println("🚀 Multi-MCP Orchestration Demo")
        println("=" .repeat(50))
        
        // Register servers
        McpServerRegistry.registerServer(McpServer.fitnessServer(8081))
        McpServerRegistry.registerServer(McpServer.reminderServer(8082))
        
        println("\n✅ Registered servers:")
        McpServerRegistry.getAllServers().forEach { server ->
            println("   - ${server.name} (${server.baseUrl})")
            println("     Tools: ${server.tools.joinToString(", ")}")
        }
        
        // Create handler
        val handler = McpJsonRpcHandler()
        
        // Create orchestrator
        val orchestrator = MultiServerOrchestrator(
            registry = McpServerRegistry,
            handler = handler
        )
        
        // Create trace logger
        val traceLogger = TraceLogger()
        
        println("\n🎯 Demonstrating cross-server flow:")
        println("   Step 1: search_fitness_logs (fitness server)")
        println("   Step 2: summarize_fitness_logs (fitness server)")
        println("   Step 3: save_summary_to_file (fitness server)")
        println("   Step 4: create_reminder (reminder server)")
        println()
        
        // Create flow context
        val flowContext = CrossServerFlowContext(
            flowId = "fitness-summary-reminder-flow",
            flowName = "fitness_summary_to_reminder_flow",
            steps = listOf(
                CrossServerFlowStep(
                    stepId = "search_logs",
                    serverId = "fitness-server-1",
                    toolName = "search_fitness_logs",
                    inputMapping = mapOf("period" to "last_7_days"),
                    outputMapping = mapOf("entries" to "step1_entries")
                ),
                CrossServerFlowStep(
                    stepId = "summarize_logs",
                    serverId = "fitness-server-1",
                    toolName = "summarize_fitness_logs",
                    inputMapping = mapOf(
                        "period" to "last_7_days",
                        "entries" to "\$step1_entries"
                    ),
                    outputMapping = mapOf("summary" to "step2_summary"),
                    dependsOn = listOf("search_logs")
                ),
                CrossServerFlowStep(
                    stepId = "save_summary",
                    serverId = "fitness-server-1",
                    toolName = "save_summary_to_file",
                    inputMapping = mapOf(
                        "period" to "last_7_days",
                        "entriesCount" to "7",
                        "avgWeight" to "82.5",
                        "workoutsCompleted" to "5",
                        "avgSteps" to "8000",
                        "avgSleepHours" to "7.2",
                        "avgProtein" to "160",
                        "summaryText" to "Weekly fitness summary generated",
                        "format" to "json"
                    ),
                    outputMapping = mapOf("filePath" to "step3_filePath"),
                    dependsOn = listOf("summarize_logs")
                ),
                CrossServerFlowStep(
                    stepId = "create_reminder",
                    serverId = "reminder-server-1",
                    toolName = "create_reminder",
                    inputMapping = mapOf(
                        "type" to "WORKOUT",
                        "title" to "Weekly Fitness Review",
                        "message" to "Review your weekly fitness progress",
                        "time" to "09:00",
                        "daysOfWeek" to "SUNDAY"
                    ),
                    outputMapping = mapOf("reminderId" to "step4_reminderId"),
                    dependsOn = listOf("summarize_logs")
                )
            )
        )
        
        // Execute flow
        val result = kotlinx.coroutines.runBlocking {
            orchestrator.executeFlow(flowContext)
        }
        
        println("\n" + "=".repeat(50))
        println("📊 Flow Result:")
        println("=".repeat(50))
        println("Success: ${result.success}")
        println("Flow Name: ${result.flowName}")
        println("Flow ID: ${result.flowId}")
        println("Steps Executed: ${result.stepsExecuted}/${result.totalSteps}")
        println("Duration: ${result.durationMs}ms")
        
        if (!result.success) {
            println("Error: ${result.errorMessage}")
        }
        
        println("\n📋 Execution Steps:")
        result.executionSteps.forEach { step ->
            println("   Step: ${step.stepId}")
            println("     Server: ${step.serverId}")
            println("     Tool: ${step.toolName}")
            println("     Status: ${step.status}")
            println("     Duration: ${step.durationMs}ms")
            if (step.error != null) {
                println("     Error: ${step.error}")
            }
            println()
        }
        
        if (result.success) {
            println("✅ Flow completed successfully!")
        } else {
            println("❌ Flow failed!")
        }
        
        println("\n" + "=".repeat(50))
    }
}
