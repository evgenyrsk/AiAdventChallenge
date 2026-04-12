package com.example.mcp.server.orchestration

import com.example.mcp.server.handler.McpJsonRpcHandler
import com.example.mcp.server.registry.McpServerRegistry
import com.example.mcp.server.registry.McpToolRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray

class MultiServerOrchestrator(
    private val registry: McpServerRegistry = McpServerRegistry,
    private val toolRouter: McpToolRouter = McpToolRouter(),
    private val handler: McpJsonRpcHandler? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun executeFlow(flowContext: CrossServerFlowContext): CrossServerFlowResult {
        println("🚀 Executing flow: ${flowContext.flowName} (${flowContext.flowId})")
        println("📋 Total steps: ${flowContext.steps.size}")

        flowContext.status = FlowStatus.IN_PROGRESS
        val executionSteps = mutableListOf<ExecutionStepResult>()

        for (step in flowContext.steps) {
            println("⏭️  Executing step: ${step.stepId} -> ${step.toolName} (server: ${step.serverId})")

            var stepResult = ExecutionStepResult(
                stepId = step.stepId,
                serverId = step.serverId,
                toolName = step.toolName,
                status = "RUNNING",
                durationMs = 0,
                output = null,
                error = null
            )

            stepResult = executeStep(flowContext, step, stepResult)
            executionSteps.add(stepResult)

            println("   ${if (stepResult.status == "COMPLETED") "✅" else "❌"} Step ${step.stepId} completed: ${stepResult.status} (${stepResult.durationMs}ms)")
            if (stepResult.output != null && stepResult.output.length < 200) {
                println("   Output: ${stepResult.output.take(100)}...")
            }

            if (stepResult.status == "FAILED") {
                println("❌ Step ${step.stepId} failed: ${stepResult.error}")
                flowContext.markAsFailed(stepResult.error ?: "Unknown error")
                return CrossServerFlowResult(
                    success = false,
                    flowName = flowContext.flowName,
                    flowId = flowContext.flowId,
                    stepsExecuted = executionSteps.size,
                    totalSteps = flowContext.totalSteps,
                    finalResult = null,
                    executionSteps = executionSteps,
                    durationMs = flowContext.durationMs,
                    errorMessage = stepResult.error
                )
            }
        }

        flowContext.markAsCompleted()
        println("✅ Flow ${flowContext.flowName} completed successfully!")
        println("📊 Steps executed: ${executionSteps.size}/${flowContext.steps.size}")
        println("⏱️  Total duration: ${flowContext.durationMs}ms")

        val finalResultJson = buildJsonObject {
            flowContext.stepResults.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Double -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
        
        return CrossServerFlowResult(
            success = true,
            flowName = flowContext.flowName,
            flowId = flowContext.flowId,
            stepsExecuted = flowContext.totalSteps,
            totalSteps = flowContext.totalSteps,
            finalResult = finalResultJson,
            executionSteps = executionSteps,
            durationMs = flowContext.durationMs,
            errorMessage = null
        )
    }
    
    suspend fun executeStep(
        flowContext: CrossServerFlowContext,
        step: CrossServerFlowStep,
        stepResult: ExecutionStepResult
    ): ExecutionStepResult {
        val startTime = System.currentTimeMillis()
        var mutableResult = stepResult
        
        try {
            val inputParams = prepareInputParameters(flowContext, step)

            if (handler != null) {
                val result = invokeTool(handler, step.toolName, inputParams)

                val outputText = try {
                    if (result is kotlinx.serialization.json.JsonObject) {
                        result.jsonObject["message"]?.toString() ?: result.toString()
                    } else {
                        result.toString()
                    }
                } catch (e: Exception) {
                    result.toString()
                }

                mutableResult = mutableResult.copy(
                    output = outputText,
                    status = "COMPLETED"
                )
                
                flowContext.setStepResult(step.stepId, result)
                
                val duration = System.currentTimeMillis() - startTime
                mutableResult = mutableResult.copy(durationMs = duration)
                
            } else {
                mutableResult = mutableResult.copy(
                    status = "FAILED",
                    error = "Handler not initialized"
                )
            }
        } catch (e: Exception) {
            mutableResult = mutableResult.copy(
                status = "FAILED",
                error = e.message ?: "Unknown error"
            )
        }
        
        return mutableResult.copy(durationMs = System.currentTimeMillis() - startTime)
    }
    
    private fun prepareInputParameters(
        flowContext: CrossServerFlowContext,
        step: CrossServerFlowStep
    ): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>()
        
        step.inputMapping.forEach { (key, mapping) ->
            if (mapping.startsWith("$")) {
                val reference = mapping.substring(1)
                val parts = reference.split(".")
                
                if (parts.isNotEmpty() && parts[0] == "stepResults") {
                    val stepId = parts[1]
                    val result = flowContext.getStepResult(stepId)
                    if (result is JsonElement) {
                        val path = if (parts.size > 2) parts.drop(2).joinToString(".") else ""
                        params[key] = extractValue(result, path)
                    }
                } else if (parts.isNotEmpty() && flowContext.stepResults.containsKey(parts[0])) {
                    params[key] = flowContext.stepResults[parts[0]]
                }
            } else {
                params[key] = mapping
            }
        }
        
        return params
    }
    
    private fun extractValue(json: JsonElement, path: String): Any? {
        if (path.isEmpty()) return json

        val parts = path.split(".")
        var current: JsonElement = json

        for (part in parts) {
            current = when (current) {
                is JsonObject -> {
                    current.jsonObject[part] ?: return null
                }
                else -> return null
            }
        }

        return when (current) {
            is JsonPrimitive -> {
                when {
                    current.isString -> current.content
                    current.content.toBooleanStrictOrNull() != null -> current.content.toBooleanStrict()
                    current.content.toDoubleOrNull() != null -> current.content.toDouble()
                    else -> current.content
                }
            }
            is JsonArray -> {
                current.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content
                        else -> element
                    }
                }
            }
            is JsonObject -> {
                current.jsonObject.toMap()
            }
            else -> current
        }
    }
    
    private suspend fun invokeTool(
        handler: McpJsonRpcHandler,
        toolName: String,
        params: Map<String, Any?>
    ): JsonElement = withContext(Dispatchers.IO) {
        val paramsJson = buildJsonObject {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Double -> put(key, value)
                    is Boolean -> put(key, value)
                    is List<*> -> {
                        val jsonArray = buildJsonArray {
                            value.forEach { item ->
                                add(JsonPrimitive(item?.toString() ?: ""))
                            }
                        }
                        put(key, jsonArray)
                    }
                    else -> put(key, value.toString())
                }
            }
        }
        
        val request = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", toolName)
            put("params", paramsJson)
        }
        
        val response = handler.handle(request.toString())
        val responseJson = json.parseToJsonElement(response)
        
        return@withContext responseJson.jsonObject["result"] ?: responseJson
    }
    
    fun isCrossServerFlow(toolName: String): Boolean {
        return toolName == "execute_multi_server_flow" || toolName == "run_cross_server_flow"
    }

    private fun isFitnessAnalysisRequest(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val fitnessKeywords = listOf(
            "фитнес", "тренировк", "спорт", "workout", "fitness",
            "лог", "запис", "log",
            "сводк", "статистик", "анализ", "summary",
            "напомин", "напомни", "напомн", "напомни", "reminder"
        )
        return fitnessKeywords.any { lower.contains(it) }
    }

    private fun createFitnessToReminderFlow(): CrossServerFlowContext {
        return CrossServerFlowContext(
            flowId = "fitness_summary_to_reminder_flow",
            flowName = "fitness_summary_to_reminder",
            steps = listOf(
                CrossServerFlowStep(
                    stepId = "search_logs",
                    serverId = "fitness-server-1",
                    toolName = "search_fitness_logs",
                    inputMapping = mapOf("period" to "last_7_days"),
                    outputMapping = mapOf("result" to "step1_result")
                ),
                CrossServerFlowStep(
                    stepId = "summarize_logs",
                    serverId = "fitness-server-1",
                    toolName = "summarize_fitness_logs",
                    inputMapping = mapOf(
                        "period" to "\$stepResults.search_logs.toolResult.data.period",
                        "entries" to "\$stepResults.search_logs.toolResult.data.entries"
                    ),
                    outputMapping = mapOf("result" to "step2_result"),
                    dependsOn = listOf("search_logs")
                ),
                CrossServerFlowStep(
                    stepId = "save_summary",
                    serverId = "fitness-server-1",
                    toolName = "save_summary_to_file",
                    inputMapping = mapOf(
                        "period" to "\$stepResults.summarize_logs.toolResult.data.period",
                        "entriesCount" to "\$stepResults.summarize_logs.toolResult.data.entriesCount",
                        "avgWeight" to "\$stepResults.summarize_logs.toolResult.data.avgWeight",
                        "workoutsCompleted" to "\$stepResults.summarize_logs.toolResult.data.workoutsCompleted",
                        "avgSteps" to "\$stepResults.summarize_logs.toolResult.data.avgSteps",
                        "avgSleepHours" to "\$stepResults.summarize_logs.toolResult.data.avgSleepHours",
                        "avgProtein" to "\$stepResults.summarize_logs.toolResult.data.avgProtein",
                        "summaryText" to "\$stepResults.summarize_logs.toolResult.data.summaryText",
                        "format" to "json"
                    ),
                    outputMapping = mapOf("result" to "step3_result"),
                    dependsOn = listOf("summarize_logs")
                ),
                CrossServerFlowStep(
                    stepId = "create_reminder",
                    serverId = "reminder-server-1",
                    toolName = "create_reminder_from_summary",
                    inputMapping = mapOf(
                        "period" to "\$stepResults.summarize_logs.toolResult.data.period",
                        "workoutsCompleted" to "\$stepResults.summarize_logs.toolResult.data.workoutsCompleted",
                        "avgSleepHours" to "\$stepResults.summarize_logs.toolResult.data.avgSleepHours",
                        "avgSteps" to "\$stepResults.summarize_logs.toolResult.data.avgSteps",
                        "avgProtein" to "\$stepResults.summarize_logs.toolResult.data.avgProtein",
                        "summaryText" to "\$stepResults.summarize_logs.toolResult.data.summaryText",
                        "minWorkouts" to "3",
                        "minSleepHours" to "7.0",
                        "minSteps" to "7000",
                        "minProtein" to "120"
                    ),
                    outputMapping = mapOf("result" to "step4_result"),
                    dependsOn = listOf("summarize_logs")
                )
            )
        )
    }

    suspend fun handleMultiServerRequest(
        prompt: String,
        handler: McpJsonRpcHandler
    ): CrossServerFlowResult {
        println("🔍 Handling multi-server request for prompt: $prompt")

        if (isFitnessAnalysisRequest(prompt)) {
            println("✅ Detected fitness analysis request, using predefined flow")
            val flowContext = createFitnessToReminderFlow()
            return executeFlow(flowContext)
        }

        val selector = AgentToolSelector()
        val selectedTools = selector.selectForPrompt(prompt)

        println("✅ Selected ${selectedTools.size} tools:")
        selectedTools.forEach { selection ->
            println("   - ${selection.toolName} (server: ${selection.serverName}, confidence: ${selection.confidence})")
        }

        if (selectedTools.isEmpty()) {
            println("⚠️ No tools found for prompt: $prompt")
            println("📋 Available tools:")
            registry.getAllTools().forEach { (toolName, serverId) ->
                val server = registry.getServerById(serverId)
                println("   - $toolName (server: ${server?.name ?: serverId})")
            }
            return CrossServerFlowResult(
                success = false,
                flowName = "auto-generated",
                flowId = "",
                stepsExecuted = 0,
                totalSteps = 0,
                finalResult = null,
                executionSteps = emptyList(),
                durationMs = 0,
                errorMessage = "No tools found for prompt. Available tools: ${registry.getAllTools().keys.joinToString(", ")}"
            )
        }

        val flowContext = createFlowFromPrompt(prompt, selectedTools)
        println("🚀 Created flow: ${flowContext.flowName} with ${flowContext.steps.size} steps")
        return executeFlow(flowContext)
    }
    
    private fun createFlowFromPrompt(
        prompt: String,
        toolSelections: List<ToolSelection>
    ): CrossServerFlowContext {
        val steps = toolSelections.mapIndexed { index, selection ->
            CrossServerFlowStep(
                stepId = "step_$index",
                serverId = selection.serverId,
                toolName = selection.toolName,
                inputMapping = emptyMap(),
                outputMapping = emptyMap(),
                dependsOn = if (index > 0) listOf("step_${index - 1}") else emptyList()
            )
        }
        
        return CrossServerFlowContext(
            flowId = "auto-generated-${System.currentTimeMillis()}",
            flowName = "auto-generated: ${prompt.take(50)}",
            steps = steps
        )
    }
}
