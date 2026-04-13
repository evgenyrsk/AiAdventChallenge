package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase

class McpToolOrchestratorImpl(
    private val callMcpToolUseCase: CallMcpToolUseCase
) : McpToolOrchestrator {
    
    private val TAG = "McpToolOrchestrator"
    
    override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
        Log.d(TAG, "🔍 Checking for MCP tool in user input...")

        val intent = extractFitnessIntent(userInput)
        
        val toolCalls = selectTools(intent)
        
        if (toolCalls.isEmpty()) {
            Log.d(TAG, "ℹ️ No MCP tools needed for this request")
            return ToolExecutionResult.NoToolFound
        }
        
        Log.d(TAG, "✅ Detected ${toolCalls.size} MCP tools to call")
        toolCalls.forEach { call ->
            Log.d(TAG, "   - ${call.tool} (depends on: ${call.dependsOn ?: "none"})")
        }

        return try {
            val results = executeTools(toolCalls)
            val context = formatResultsForLLM(results)
            Log.d(TAG, "📝 MCP Context to add to LLM (length=${context.length}):")
            Log.d(TAG, context)
            ToolExecutionResult.Success(context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute MCP tools", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
    
    private fun extractFitnessIntent(userInput: String): com.example.aiadventchallenge.domain.model.mcp.FitnessIntent {
        val lowerInput = userInput.lowercase()
        
        val needsNutritionMetrics = listOf(
            "калори", "bmr", "tdee", "питани", "раcсчит", "расчет",
            "calories", "nutrition", "macros", "protein", "fat", "carbs"
        ).any { lowerInput.contains(it) }
        
        val needsMealGuidance = listOf(
            "питани", "ед", "рецепт", "прием пищ", "еда",
            "meal", "food", "diet", "eating", "nutrition plan"
        ).any { lowerInput.contains(it) }
        
        val needsTrainingGuidance = listOf(
            "тренировк", "спорт", "фитнес", "упражнен", "зал",
            "workout", "training", "exercise", "fitness", "gym"
        ).any { lowerInput.contains(it) }
        
        val extractedParams = extractParameters(userInput)
        
        return com.example.aiadventchallenge.domain.model.mcp.FitnessIntent(
            needsNutritionMetrics = needsNutritionMetrics || needsMealGuidance || needsTrainingGuidance,
            needsMealGuidance = needsMealGuidance || needsNutritionMetrics,
            needsTrainingGuidance = needsTrainingGuidance || needsNutritionMetrics,
            extractedParams = extractedParams
        )
    }
    
    private fun extractParameters(userInput: String): Map<String, Any?> {
        val lowerInput = userInput.lowercase()
        val params = mutableMapOf<String, Any?>()
        
        val ageRegex = """(\d+)\s*(?:лет|год|годов|years?|y)""".toRegex(RegexOption.IGNORE_CASE)
        ageRegex.find(userInput)?.let {
            params["age"] = it.groupValues[1].toInt()
        }
        
        val heightRegex = """(\d+)\s*(?:см|cm)""".toRegex(RegexOption.IGNORE_CASE)
        heightRegex.find(userInput)?.let {
            params["heightCm"] = it.groupValues[1].toInt()
        }
        
        val weightRegex = """(\d+(?:\.\d+)?)\s*(?:кг|kg)""".toRegex(RegexOption.IGNORE_CASE)
        weightRegex.find(userInput)?.let {
            params["weightKg"] = it.groupValues[1].toDouble()
        }
        
        if (lowerInput.contains("мужчин") || lowerInput.contains("мужской")) {
            params["sex"] = "male"
        } else if (lowerInput.contains("женщин") || lowerInput.contains("женский")) {
            params["sex"] = "female"
        }
        
        if (lowerInput.contains("похуд") || lowerInput.contains("weight los") || lowerInput.contains("сброс")) {
            params["goal"] = "weight_loss"
        } else if (lowerInput.contains("набор") || lowerInput.contains("muscle") || lowerInput.contains("масс")) {
            params["goal"] = "muscle_gain"
        } else if (lowerInput.contains("поддержа") || lowerInput.contains("maintenanc")) {
            params["goal"] = "maintenance"
        }
        
        if (lowerInput.contains("сидяч") || lowerInput.contains("sedentary")) {
            params["activityLevel"] = "sedentary"
        } else if (lowerInput.contains("лёгк") || lowerInput.contains("light")) {
            params["activityLevel"] = "light"
        } else if (lowerInput.contains("умерен") || lowerInput.contains("moderate")) {
            params["activityLevel"] = "moderate"
        } else if (lowerInput.contains("активн") || lowerInput.contains("active")) {
            params["activityLevel"] = "active"
        } else if (lowerInput.contains("очень активн") || lowerInput.contains("very active")) {
            params["activityLevel"] = "very_active"
        }
        
        val daysRegex = """(\d+)\s*(?:раз|раза|times?)""".toRegex(RegexOption.IGNORE_CASE)
        daysRegex.find(userInput)?.let {
            params["trainingDaysPerWeek"] = it.groupValues[1].toInt()
        }
        
        val mealsRegex = """(\d+)\s*(?:раз|раза|times?)\s*(?:в день|день|day)""".toRegex(RegexOption.IGNORE_CASE)
        mealsRegex.find(userInput)?.let {
            params["mealsPerDay"] = it.groupValues[1].toInt()
        }
        
        return params
    }
    
    private fun applyDefaults(params: Map<String, Any?>): Map<String, Any?> {
        val result = params.toMutableMap()
        
        // Обязательные параметры для calculate_nutrition_metrics
        if (!result.contains("sex")) result["sex"] = "male"
        if (!result.contains("age")) result["age"] = 30
        if (!result.contains("heightCm")) result["heightCm"] = 175
        if (!result.contains("weightKg")) result["weightKg"] = 70.0
        if (!result.contains("activityLevel")) result["activityLevel"] = "moderate"
        if (!result.contains("goal")) result["goal"] = "maintenance"
        
        // Опциональные параметры
        if (!result.contains("trainingDaysPerWeek")) result["trainingDaysPerWeek"] = 3
        if (!result.contains("mealsPerDay")) result["mealsPerDay"] = 3
        if (!result.contains("trainingLevel")) result["trainingLevel"] = "beginner"
        
        return result
    }
    
    private fun logAppliedDefaults(originalParams: Map<String, Any?>, finalParams: Map<String, Any?>) {
        val defaultApplied = finalParams.keys.filterNot { originalParams.containsKey(it) }
        
        if (defaultApplied.isNotEmpty()) {
            Log.d(TAG, "📝 Applied defaults for missing parameters:")
            defaultApplied.forEach { key ->
                Log.d(TAG, "   - $key = ${finalParams[key]}")
            }
        }
    }
    
    private sealed class ValidationResult {
        data object Valid : ValidationResult()
        data class MissingParams(val missingParams: List<String>, val toolName: String) : ValidationResult()
    }
    
    private fun validateToolParams(toolName: String, params: Map<String, Any?>): ValidationResult {
        val requiredParams = when (toolName) {
            "calculate_nutrition_metrics" -> listOf("sex", "age", "heightCm", "weightKg", "activityLevel", "goal")
            "generate_meal_guidance" -> listOf("goal", "targetCalories", "proteinG", "fatG", "carbsG")
            "generate_training_guidance" -> listOf("goal")
            else -> emptyList()
        }
        
        val missing = requiredParams.filterNot { params.containsKey(it) }
        
        return if (missing.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.MissingParams(missing, toolName)
        }
    }
    
    private fun selectTools(intent: com.example.aiadventchallenge.domain.model.mcp.FitnessIntent): List<com.example.aiadventchallenge.domain.model.mcp.ToolCall> {
        val calls = mutableListOf<com.example.aiadventchallenge.domain.model.mcp.ToolCall>()
        val paramsWithDefaults = applyDefaults(intent.extractedParams)
        
        logAppliedDefaults(intent.extractedParams, paramsWithDefaults)
        
        if (intent.needsNutritionMetrics) {
            val nutritionParams = paramsWithDefaults.filterKeys {
                it in listOf("sex", "age", "heightCm", "weightKg", "activityLevel", "goal")
            }
            
            calls.add(
                com.example.aiadventchallenge.domain.model.mcp.ToolCall(
                    tool = "calculate_nutrition_metrics",
                    params = nutritionParams,
                    dependsOn = null
                )
            )
        }
        
        if (intent.needsMealGuidance) {
            val mealParams = paramsWithDefaults
            
            calls.add(
                com.example.aiadventchallenge.domain.model.mcp.ToolCall(
                    tool = "generate_meal_guidance",
                    params = mealParams.filterKeys {
                        it in listOf("goal", "mealsPerDay", "dietaryPreferences", "dietaryRestrictions")
                    },
                    dependsOn = "calculate_nutrition_metrics"
                )
            )
        }
        
        if (intent.needsTrainingGuidance) {
            val trainingParams = paramsWithDefaults
            
            calls.add(
                com.example.aiadventchallenge.domain.model.mcp.ToolCall(
                    tool = "generate_training_guidance",
                    params = trainingParams.filterKeys {
                        it in listOf("goal", "trainingLevel", "trainingDaysPerWeek", "sessionDurationMinutes", "availableEquipment", "restrictions")
                    },
                    dependsOn = "calculate_nutrition_metrics"
                )
            )
        }
        
        return calls
    }
    
    private suspend fun executeTools(calls: List<com.example.aiadventchallenge.domain.model.mcp.ToolCall>): Map<String, Any> {
        val results = mutableMapOf<String, Any>()
        val executionResults = mutableListOf<com.example.aiadventchallenge.domain.model.mcp.ToolExecutionResult>()
        
        for (call in calls) {
            val startTime = System.currentTimeMillis()
            val serverId = getServerIdForTool(call.tool)
            
            Log.d(TAG, "🔧 Executing tool: ${call.tool} (server: $serverId)")
            
            val finalParams = if (call.dependsOn != null) {
                prepareParamsWithDependency(call, results)
            } else {
                call.params
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            try {
                val validationResult = validateToolParams(call.tool, finalParams)
                if (validationResult is ValidationResult.MissingParams) {
                    val missingParamsText = validationResult.missingParams.joinToString(", ")
                    val paramHints = when (validationResult.toolName) {
                        "calculate_nutrition_metrics" -> """
                            |Пример: "Рассчитай калории для мужчины 30 лет, ростом 175 см, весом 70 кг с умеренной активностью для похудения"
                            |Возможные цели: weight_loss (похудение), maintenance (поддержание веса), muscle_gain (набор массы)
                            |Уровни активности: sedentary, light, moderate, active, very_active
                        """.trimMargin()
                        "generate_meal_guidance" -> """
                            |Для генерации плана питания сначала нужно рассчитать метрики питания
                            |Пример: "Рассчитай калории и составь план питания"
                        """.trimMargin()
                        "generate_training_guidance" -> """
                            |Пример: "Составь план тренировок для набора массы"
                        """.trimMargin()
                        else -> ""
                    }
                    val error = """
                        |Для инструмента ${validationResult.toolName} отсутствуют обязательные параметры: $missingParamsText
                        |
                        |$paramHints
                    """.trimMargin()
                    Log.e(TAG, "❌ $error")
                    throw Exception(error.trim())
                }
                
                val mcpData = callMcpToolUseCase(call.tool, finalParams)
                
                val result = when (mcpData) {
                    is McpToolData.NutritionMetrics -> mcpData.result
                    is McpToolData.MealGuidance -> mcpData.result
                    is McpToolData.TrainingGuidance -> mcpData.result
                    is McpToolData.StringResult -> mcpData.message
                    else -> throw Exception("Unknown tool data type")
                }
                
                results[call.tool] = result
                
                val toolExecutionResult = com.example.aiadventchallenge.domain.model.mcp.ToolExecutionResult(
                    tool = call.tool,
                    serverId = serverId,
                    success = true,
                    durationMs = System.currentTimeMillis() - startTime,
                    result = result,
                    error = null
                )
                executionResults.add(toolExecutionResult)
                
                Log.d(TAG, "✅ Tool ${call.tool} completed in ${toolExecutionResult.durationMs}ms")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Tool ${call.tool} failed", e)
                
                val toolExecutionResult = com.example.aiadventchallenge.domain.model.mcp.ToolExecutionResult(
                    tool = call.tool,
                    serverId = serverId,
                    success = false,
                    durationMs = System.currentTimeMillis() - startTime,
                    result = null,
                    error = e.message
                )
                executionResults.add(toolExecutionResult)
                
                throw e
            }
        }
        
        results["executionSteps"] = executionResults
        return results
    }
    
    private fun prepareParamsWithDependency(
        call: com.example.aiadventchallenge.domain.model.mcp.ToolCall,
        results: Map<String, Any>
    ): Map<String, Any?> {
        val params = mutableMapOf<String, Any?>()
        
        if (call.dependsOn == "calculate_nutrition_metrics") {
            val nutritionResult = results["calculate_nutrition_metrics"] as? com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse
            if (nutritionResult != null) {
                when (call.tool) {
                    "generate_meal_guidance" -> {
                        params["goal"] = call.params["goal"] ?: "maintenance"
                        params["targetCalories"] = nutritionResult.targetCalories
                        params["proteinG"] = nutritionResult.proteinG
                        params["fatG"] = nutritionResult.fatG
                        params["carbsG"] = nutritionResult.carbsG
                    }
                    "generate_training_guidance" -> {
                        params["goal"] = call.params["goal"] ?: "maintenance"
                    }
                }
            }
        }
        
        params.putAll(call.params)
        return params
    }
    
    private fun getServerIdForTool(toolName: String): String {
        return when (toolName) {
            "calculate_nutrition_metrics" -> "nutrition-metrics-server-1"
            "generate_meal_guidance" -> "meal-guidance-server-1"
            "generate_training_guidance" -> "training-guidance-server-1"
            else -> "unknown-server"
        }
    }
    
    private fun formatResultsForLLM(results: Map<String, Any>): String {
        val executionSteps = results["executionSteps"] as? List<com.example.aiadventchallenge.domain.model.mcp.ToolExecutionResult>
        val stepsText = executionSteps?.joinToString("\n") { step ->
            val statusEmoji = if (step.success) "✅" else "❌"
            "$statusEmoji ${step.serverId} → ${step.tool} (${step.durationMs}ms)"
        } ?: ""
        
        val nutritionResult = results["calculate_nutrition_metrics"] as? com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse
        val mealResult = results["generate_meal_guidance"] as? com.example.aiadventchallenge.domain.model.mcp.MealGuidanceResponse
        val trainingResult = results["generate_training_guidance"] as? com.example.aiadventchallenge.domain.model.mcp.TrainingGuidanceResponse
        
        val content = buildString {
            appendLine("================================================================================")
            appendLine("🏋️ FITNESS MCP FLOW - РЕЗУЛЬТАТЫ ВЫПОЛНЕНИЯ")
            appendLine("================================================================================")
            appendLine()
            
            if (nutritionResult != null) {
                appendLine("📊 NUTRITION METRICS:")
                appendLine("   BMR: ${nutritionResult.bmr} ккал")
                appendLine("   TDEE: ${nutritionResult.tdee} ккал")
                appendLine("   Target Calories: ${nutritionResult.targetCalories} ккал")
                appendLine("   Protein: ${nutritionResult.proteinG}г")
                appendLine("   Fat: ${nutritionResult.fatG}г")
                appendLine("   Carbs: ${nutritionResult.carbsG}г")
                appendLine("   Notes: ${nutritionResult.notes}")
                appendLine()
            }
            
            if (mealResult != null) {
                appendLine("🍽️ MEAL GUIDANCE:")
                appendLine("   Strategy: ${mealResult.mealStrategy}")
                appendLine("   Recommended Foods: ${mealResult.recommendedFoods.joinToString(", ")}")
                appendLine("   Foods to Limit: ${mealResult.foodsToLimit.joinToString(", ")}")
                appendLine("   Notes: ${mealResult.notes}")
                appendLine()
            }
            
            if (trainingResult != null) {
                appendLine("💪 TRAINING GUIDANCE:")
                appendLine("   Split: ${trainingResult.trainingSplit}")
                appendLine("   Principles: ${trainingResult.exercisePrinciples}")
                appendLine("   Recovery: ${trainingResult.recoveryNotes}")
                appendLine("   Notes: ${trainingResult.notes}")
                appendLine()
            }
            
            if (stepsText.isNotEmpty()) {
                appendLine("Шаги выполнения:")
                append(stepsText)
                appendLine()
            }
            
            appendLine("================================================================================")
        }
        
        return content
    }
}
