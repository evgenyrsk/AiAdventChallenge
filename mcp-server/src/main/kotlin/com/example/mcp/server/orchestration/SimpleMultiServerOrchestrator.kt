package com.example.mcp.server.orchestration

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import com.example.mcp.server.model.reminder.Reminder
import com.example.mcp.server.model.reminder.ReminderType
import com.example.mcp.server.model.reminder.DayOfWeek
import java.time.Instant

class SimpleMultiServerOrchestrator(
    private val httpClient: OkHttpClient
) {
    
    suspend fun executeFlow(flowContext: CrossServerFlowContext): CrossServerFlowResult {
        val executionSteps = mutableListOf<com.example.mcp.server.orchestration.ExecutionStepResult>()
        var stepIndex = 0
        
        for (step in flowContext.steps) {
            val startTime = System.currentTimeMillis()
            
            try {
                val toolResult = callTool(step.serverId, step.toolName, prepareParams(flowContext, step))
                
                executionSteps.add(com.example.mcp.server.orchestration.ExecutionStepResult(
                    stepId = step.stepId,
                    serverId = step.serverId,
                    toolName = step.toolName,
                    status = "COMPLETED",
                    durationMs = System.currentTimeMillis() - startTime,
                    output = toolResult?.toString(),
                    error = null
                ))
                
                stepIndex++
            } catch (e: Exception) {
                executionSteps.add(com.example.mcp.server.orchestration.ExecutionStepResult(
                    stepId = step.stepId,
                    serverId = step.serverId,
                    toolName = step.toolName,
                    status = "FAILED",
                    durationMs = System.currentTimeMillis() - startTime,
                    output = null,
                    error = e.message
                ))
            }
        }
        
        return com.example.mcp.server.orchestration.CrossServerFlowResult(
            success = true,
            flowName = flowContext.flowName,
            flowId = flowContext.flowId,
            stepsExecuted = flowContext.steps.size,
            totalSteps = flowContext.steps.size,
            finalResult = null,
            executionSteps = executionSteps,
            durationMs = System.currentTimeMillis() - flowContext.startedAt,
            errorMessage = null
        )
    }
    
    private suspend fun callTool(
        serverId: String,
        toolName: String,
        params: Map<String, Any?>
    ): String? {
        return when (toolName) {
            "search_fitness_logs" -> callSearchFitnessLogs(serverId, params)
            "summarize_fitness_logs" -> callSummarizeFitnessLogs(serverId, params)
            "save_summary_to_file" -> callSaveSummaryToFile(serverId, params)
            "create_reminder" -> callCreateReminder(serverId, params)
            else -> return null
        }
    }

    private fun prepareParams(
        flowContext: CrossServerFlowContext,
        step: com.example.mcp.server.orchestration.CrossServerFlowStep
    ): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>()

        for ((key, value) in step.inputMapping) {
            params[key] = resolveValue(value, flowContext)
        }

        return params
    }

    private fun resolveValue(
        value: Any?,
        flowContext: CrossServerFlowContext
    ): Any? {
        if (value is String && value.startsWith("$.")) {
            val reference = value.substring(2)
            return flowContext.getStepResult(reference)
        }
        return value
    }
    
    private fun callSearchFitnessLogs(serverId: String, params: Map<String, Any?>): String? {
        // Simulated call to fitness server
        val period = params["period"] as? String ?: "last_7_days"
        
        // Simulated result
        val entriesJson = """[
            {"date": "2026-04-12", "weight": 82.3, "calories": 2400, "protein": 160, "workout": true, "steps": 8000, "sleepHours": 7.2, "notes": ""}
        """
        
        return """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": {
                "entries": $entriesJson
            },
            "error": null
        }
        """
    }
    
    private fun callSummarizeFitnessLogs(serverId: String, params: Map<String, Any?>): String? {
        val entriesJson = params["entries"] as? String ?: "[]"
        
        val entries = com.example.mcp.server.model.fitness.FitnessLog.parseList(entriesJson)

        val avgWeight = entries.mapNotNull { it.weight }.average()
        val workoutsCompleted = entries.count { it.workoutCompleted }
        val avgSteps = entries.mapNotNull { it.steps }.average()
        
        val summaryText = """
            Weekly fitness summary:
            Period: last_7_days
            Entries: ${entries.size}
            Workouts: ${workoutsCompleted}
            Avg weight: ${"%.1f"} kg
            Avg steps: ${"%.0f"} steps/день
            Avg sleep: ${"%.1f"} ч
            Avg protein: ${"%.0f"} г
        """.trimIndent()
        
        return """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": {
                "period": "last_7_days",
                "entriesCount": entries.size,
                "avgWeight": avgWeight,
                "workoutsCompleted": workoutsCompleted,
                "avgSteps": avgSteps,
                "avgSleepHours": entries.mapNotNullOf { it.sleepHours }.map { it.sleepHours }.average(),
                "avgProtein": entries.mapNotNullOf { it.protein }.map { it.protein }.average(),
                "summaryText": summaryText
            },
            "error": null
        }
        """
    }
    
    private fun callSaveSummaryToFile(serverId: String, params: Map<String, Any?>): String? {
        val period = params["period"] as? String ?: "last_7_days"
        val entriesCount = params["entriesCount"] as? Int ?: 7
        val avgWeight = params["avgWeight"] as? Double ?: 82.5
        val workoutsCompleted = params["workoutsCompleted"] as? Int ?: 5
        val avgSteps = params["avgSteps"] as? Int ?: 8000
        val avgSleepHours = params["avgSleepHours"] as? Double ?: 7.2
        val avgProtein = params["avgProtein"] as? Double ?: 160
        val summaryText = params["summaryText"] as? String ?: "Weekly fitness summary generated"
        val format = params["format"] as? String ?: "json"
        
        // Generate simulated file path
        val timestamp = Instant.now().toString().replace(":", "-")
        
        val filePath = "/tmp/fitness-summary-$timestamp.json"
        
        val simulatedContent = """{
    "period": "$period",
    "entriesCount": $entriesCount,
    "avgWeight": $avgWeight,
    "workoutsCompleted": $workoutsCompleted,
    "avgSteps": $avgSteps,
    "avgSleepHours": $avgSleepHours,
    "avgProtein": $avgProtein,
    "summaryText": "$summaryText",
    "format": "$format",
    "filePath": "$filePath",
    "created_at": "$timestamp"
}"""
        
        return """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": {
                "filePath": filePath,
                "created_at": timestamp,
                "period": period,
                "entriesCount": entriesCount,
                "avgWeight": avgWeight,
                "workoutsCompleted": workoutsCompleted,
                "avgSteps": avgSteps,
                "avgSleepHours": avgSleepHours,
                "avgProtein": avgProtein,
                "summaryText": summaryText
            },
            "error": null
        }
        """
    }
    
    private fun callCreateReminder(serverId: String, params: Map<String, Any?>): String? {
        val type = params["type"] as? String ?: "WORKOUT"
        val title = params["title"] as? String ?: "Weekly Fitness Review"
        val message = params["message"] as? String ?: "Review your weekly fitness progress"
        val time = params["time"] as? String ?: "09:00"
        val daysOfWeek = (params["daysOfWeek"] as? String ?: "SUNDAY").split(",").map { it.trim().uppercase() }

        val reminder = Reminder(
            id = "reminder_${System.currentTimeMillis()}",
            type = ReminderType.WORKOUT,
            title = title,
            message = message,
            time = time,
            daysOfWeek = daysOfWeek.map { DayOfWeek.valueOf(it) },
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        
        // Simulate saving reminder
        return """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "result": {
                "reminderId": reminder.id,
                "type": reminder.type,
                "title": reminder.title,
                "message": reminder.message,
                "time": reminder.time,
                "daysOfWeek": reminder.daysOfWeek.joinToString(","),
                "isActive": reminder.isActive
            },
            "error": null
        }
        """
    }
    
    companion object {
        fun createDefaultFitnessReminder(): CrossServerFlowContext {
            return CrossServerFlowContext(
                flowId = "fitness_summary_to_reminder_flow",
                flowName = "fitness_summary_to_reminder",
                steps = listOf(
                    CrossServerFlowStep(
                        stepId = "search_logs",
                        serverId = "fitness-server-1",
                        toolName = "search_fitness_logs",
                        inputMapping = mapOf("period" to "last_7_days"),
                        outputMapping = emptyMap()
                    ),
                    CrossServerFlowStep(
                        stepId = "summarize_logs",
                        serverId = "fitness-server-1",
                        toolName = "summarize_fitness_logs",
                        inputMapping = mapOf(
                            "period" to "last_7_days",
                            "entries" to "$.step1_entries"
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
        }
    }
}
