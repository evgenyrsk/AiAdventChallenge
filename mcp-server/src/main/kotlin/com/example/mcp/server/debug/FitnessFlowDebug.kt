package com.example.mcp.server.debug

import com.example.mcp.server.handler.McpJsonRpcHandler
import com.example.mcp.server.orchestration.MultiServerOrchestrator
import com.example.mcp.server.registry.McpServer
import com.example.mcp.server.registry.McpServerRegistry

/**
 * Simple debug script to test fitness flow without running full demo
 */
object FitnessFlowDebug {

    @JvmStatic
    fun main(args: Array<String>) {
        println("🔍 Fitness Flow Debug Script")
        println("=" .repeat(50))

        // Register servers
        McpServerRegistry.registerServer(McpServer.fitnessServer(8081))
        McpServerRegistry.registerServer(McpServer.reminderServer(8082))

        println("\n✅ Registered servers:")
        McpServerRegistry.getAllServers().forEach { server ->
            println("   - ${server.name} (${server.baseUrl})")
        }

        // Create handler
        val handler = McpJsonRpcHandler()

        // Create orchestrator
        val orchestrator = MultiServerOrchestrator(
            registry = McpServerRegistry,
            handler = handler
        )

        println("\n🎯 Testing multi-server request:")
        println()

        // Test the flow
        val prompt = "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"
        println("📝 Prompt: $prompt\n")

        val result = kotlinx.coroutines.runBlocking {
            orchestrator.handleMultiServerRequest(prompt, handler)
        }

        println("\n" + "=".repeat(50))
        println("📊 Flow Result:")
        println("=".repeat(50))
        println("Success: ${result.success}")
        println("Flow Name: ${result.flowName}")
        println("Steps Executed: ${result.stepsExecuted}/${result.totalSteps}")
        println("Duration: ${result.durationMs}ms")

        if (!result.success) {
            println("Error: ${result.errorMessage}")
        }

        println("\n📋 Execution Steps:")
        result.executionSteps.forEach { step ->
            println("   Step: ${step.stepId}")
            println("     Tool: ${step.toolName}")
            println("     Status: ${step.status}")
            println("     Duration: ${step.durationMs}ms")
            if (step.error != null) {
                println("     Error: ${step.error}")
            }
            if (step.output != null && step.output.length < 200) {
                println("     Output: ${step.output}")
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
