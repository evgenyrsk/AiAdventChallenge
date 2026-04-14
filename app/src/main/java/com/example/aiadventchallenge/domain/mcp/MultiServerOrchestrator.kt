package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.data.mcp.DocumentRetrievalConfig
import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class MultiServerOrchestrator(
    private val repository: MultiServerRepository
) : McpToolOrchestrator {
    private val TAG = "MultiServerOrchestrator"
    private val json = Json { ignoreUnknownKeys = true }
    
    override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
        Log.d(TAG, "🔍 Detecting tools for user input...")
        
        val intent = extractFitnessIntent(userInput)
        val toolCalls = selectTools(intent)
        
        if (toolCalls.isEmpty()) {
            Log.d(TAG, "ℹ️ No tools needed")
            return ToolExecutionResult.NoToolFound
        }
        
        Log.d(TAG, "✅ Detected ${toolCalls.size} tools to call")
        toolCalls.forEach { call ->
            val serverId = getServerIdForTool(call.tool)
            Log.d(TAG, "   - ${call.tool} (server: $serverId, depends on: ${call.dependsOn})")
        }
        
        for (call in toolCalls) {
            val validation = validateRequiredParams(call.tool, call.params)
            if (validation is ValidationResult.Invalid) {
                Log.d(TAG, "❌ Missing required parameters for ${call.tool}: ${validation.missingParams.map { it.name }}")
                return ToolExecutionResult.MissingParameters(validation.missingParams)
            }
        }
        
        val completedToolCalls = toolCalls.map { call ->
            call.copy(params = completeParamsWithDefaults(call.tool, call.params))
        }
        
        return try {
            val results = executeTools(completedToolCalls)
            val context = formatResultsForLLM(results)
            val retrievalSummary = (results["answer_with_retrieval"] as? String)?.let(::parseRetrievalSummary)
            Log.d(TAG, "📝 Context to add to LLM (length=${context.length})")
            ToolExecutionResult.Success(context, retrievalSummary)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute tools", e)
            ToolExecutionResult.Error(e.message ?: "Unknown error")
        }
    }
    
    private fun validateRequiredParams(
        tool: String,
        params: Map<String, Any?>
    ): ValidationResult {
        val requirements = getParamRequirements(tool)
        val missing = requirements.required.filter { !params.containsKey(it) }
        
        return if (missing.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(missing.map {
                ParameterInfo(
                    name = it,
                    description = getDescriptionForParam(it),
                    example = getExampleForParam(it)
                )
            })
        }
    }
    
    private fun getDescriptionForParam(param: String): String {
        return when (param) {
            "age" -> "возраст в годах"
            "sex" -> "пол (мужской/женский)"
            "heightCm" -> "рост в сантиметрах"
            "weightKg" -> "вес в килограммах"
            "activityLevel" -> "уровень активности (сидячий/легкий/умеренный/активный/очень активный)"
            "goal" -> "цель (похудение/набор массы/поддержание)"
            else -> param
        }
    }
    
    private fun getExampleForParam(param: String): String {
        return when (param) {
            "age" -> "30"
            "sex" -> "мужской"
            "heightCm" -> "180 см"
            "weightKg" -> "75 кг"
            "activityLevel" -> "умеренный"
            "goal" -> "похудение"
            else -> param
        }
    }
    
    private suspend fun executeTools(calls: List<ToolCall>): Map<String, Any> {
        val results = mutableMapOf<String, Any>()
        
        for (call in calls) {
            val serverId = getServerIdForTool(call.tool)
            val startTime = System.currentTimeMillis()
            
            Log.d(TAG, "🔧 Executing ${call.tool} on $serverId")
            
            val finalParams = if (call.dependsOn != null) {
                prepareParamsWithDependency(call, results)
            } else {
                call.params
            }
            
            val mcpData = repository.callTool(call.tool, finalParams)
            
            val result = when (mcpData) {
                is McpToolData.NutritionMetrics -> mcpData.result
                is McpToolData.MealGuidance -> mcpData.result
                is McpToolData.TrainingGuidance -> mcpData.result
                is McpToolData.StringResult -> mcpData.message
            }
            
            results[call.tool] = result
            
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "✅ ${call.tool} completed in ${duration}ms on $serverId")
        }
        
        return results
    }
    
    private fun prepareParamsWithDependency(
        call: ToolCall,
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
            "answer_with_retrieval" -> "document-index-server-1"
            else -> "unknown-server"
        }
    }
    
    private fun extractFitnessIntent(userInput: String): FitnessIntent {
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

        val needsKnowledgeRetrieval = listOf(
            "документ", "readme", "архитектур", "как устроен", "как реализован",
            "state machine", "retrieval", "rag", "индекс", "chunk", "embedding",
            "mcp", "pipeline", "код", "модул", "server", "tool", "tools"
        ).any { lowerInput.contains(it) }
        
        val extractedParams = extractParameters(userInput)
        
        return FitnessIntent(
            needsNutritionMetrics = needsNutritionMetrics || needsMealGuidance || needsTrainingGuidance,
            needsMealGuidance = needsMealGuidance,
            needsTrainingGuidance = needsTrainingGuidance,
            needsKnowledgeRetrieval = needsKnowledgeRetrieval,
            originalQuery = userInput,
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
        
        return params
    }
    
    private fun selectTools(intent: FitnessIntent): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val params = intent.extractedParams

        if (intent.needsKnowledgeRetrieval) {
            calls.add(
                ToolCall(
                    tool = "answer_with_retrieval",
                    dependsOn = null,
                    params = mapOf(
                        "query" to intent.originalQuery,
                        "source" to DocumentRetrievalConfig.defaultSource,
                        "strategy" to DocumentRetrievalConfig.DEFAULT_STRATEGY,
                        "topK" to DocumentRetrievalConfig.DEFAULT_TOP_K,
                        "maxChars" to DocumentRetrievalConfig.DEFAULT_MAX_CHARS,
                        "perDocumentLimit" to DocumentRetrievalConfig.DEFAULT_PER_DOCUMENT_LIMIT
                    )
                )
            )
        }
        
        if (intent.needsNutritionMetrics) {
            val nutritionParams = params.filterKeys {
                it in listOf("sex", "age", "heightCm", "weightKg", "activityLevel", "goal")
            }

            calls.add(
                ToolCall(
                    tool = "calculate_nutrition_metrics",
                    dependsOn = null,
                    params = nutritionParams
                )
            )
        }

        if (intent.needsMealGuidance) {
            calls.add(
                ToolCall(
                    tool = "generate_meal_guidance",
                    dependsOn = "calculate_nutrition_metrics",
                    params = params
                )
            )
        }

        if (intent.needsTrainingGuidance) {
            calls.add(
                ToolCall(
                    tool = "generate_training_guidance",
                    dependsOn = "calculate_nutrition_metrics",
                    params = params
                )
            )
        }
        
        return calls
    }
    
    private fun formatResultsForLLM(results: Map<String, Any>): String {
        val retrievalPackage = (results["answer_with_retrieval"] as? String)?.let(::formatRetrievalPackage)
        val nutritionResult = results["calculate_nutrition_metrics"] as? com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse
        val mealResult = results["generate_meal_guidance"] as? com.example.aiadventchallenge.domain.model.mcp.MealGuidanceResponse
        val trainingResult = results["generate_training_guidance"] as? com.example.aiadventchallenge.domain.model.mcp.TrainingGuidanceResponse
        
        val content = buildString {
            appendLine("================================================================================")
            appendLine("🏋️ MULTI-SERVER FITNESS FLOW - РЕЗУЛЬТАТЫ ВЫПОЛНЕНИЯ")
            appendLine("================================================================================")
            appendLine()

            if (retrievalPackage != null) {
                appendLine("📚 DOCUMENT RETRIEVAL (Server: document-index-server-1):")
                appendLine(retrievalPackage)
                appendLine()
            }
            
            if (nutritionResult != null) {
                appendLine("🥗 NUTRITION METRICS (Server: nutrition-metrics-server-1):")
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
                appendLine("🍽️ MEAL GUIDANCE (Server: meal-guidance-server-1):")
                appendLine("   Strategy: ${mealResult.mealStrategy}")
                appendLine("   Recommended Foods: ${mealResult.recommendedFoods.joinToString(", ")}")
                appendLine("   Foods to Limit: ${mealResult.foodsToLimit.joinToString(", ")}")
                appendLine("   Notes: ${mealResult.notes}")
                appendLine()
            }
            
            if (trainingResult != null) {
                appendLine("💪 TRAINING GUIDANCE (Server: training-guidance-server-1):")
                appendLine("   Split: ${trainingResult.trainingSplit}")
                appendLine("   Principles: ${trainingResult.exercisePrinciples}")
                appendLine("   Recovery: ${trainingResult.recoveryNotes}")
                appendLine("   Notes: ${trainingResult.notes}")
                appendLine()
            }
            
            if (nutritionResult != null || mealResult != null || trainingResult != null) {
                appendLine("📊 DATA FLOW:")
                appendLine("   ✅ nutrition-metrics-server-1 (8081) → calculate_nutrition_metrics")
                if (mealResult != null) {
                    appendLine("      ↓ passes targetCalories: ${nutritionResult?.targetCalories}")
                    appendLine("   ✅ meal-guidance-server-1 (8082) → generate_meal_guidance")
                }
                if (trainingResult != null) {
                    appendLine("      ↓ passes goal from nutrition")
                    appendLine("   ✅ training-guidance-server-1 (8083) → generate_training_guidance")
                }
                appendLine()
            }
            
            appendLine("================================================================================")
        }
        
        return content
    }

    private fun formatRetrievalPackage(raw: String): String {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val data = root["data"]?.jsonObject
            val retrieval = data?.get("retrieval")?.jsonObject
            val applied = data?.get("retrievalApplied")?.jsonPrimitive?.content ?: "false"
            val selectedCount = retrieval?.get("selectedCount")?.jsonPrimitive?.content ?: "0"
            val contextEnvelope = retrieval?.get("contextEnvelope")?.jsonPrimitive?.content ?: raw

            buildString {
                appendLine("   retrievalApplied: $applied")
                appendLine("   selectedChunks: $selectedCount")
                appendLine()
                append(contextEnvelope.prependIndent("   "))
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun parseRetrievalSummary(raw: String): RetrievalSummary? {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val data = root["data"]?.jsonObject ?: return null
            val query = data["query"]?.jsonPrimitive?.content ?: return null
            val retrieval = data["retrieval"]?.jsonObject ?: return null
            val source = retrieval["source"]?.jsonPrimitive?.content
                ?: DocumentRetrievalConfig.defaultSource
            val strategy = retrieval["strategy"]?.jsonPrimitive?.content
                ?: DocumentRetrievalConfig.DEFAULT_STRATEGY
            val selectedCount = retrieval["selectedCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val contextEnvelope = retrieval["contextEnvelope"]?.jsonPrimitive?.content.orEmpty()
            val chunks = retrieval["chunks"]?.jsonArray?.map { chunkElement ->
                val chunk = chunkElement.jsonObject
                RetrievalSourceCard(
                    title = chunk["title"]?.jsonPrimitive?.content.orEmpty(),
                    relativePath = chunk["relativePath"]?.jsonPrimitive?.content.orEmpty(),
                    section = chunk["section"]?.jsonPrimitive?.content.orEmpty(),
                    score = chunk["score"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                )
            }.orEmpty()

            RetrievalSummary(
                query = query,
                source = source,
                strategy = strategy,
                selectedCount = selectedCount,
                contextEnvelope = contextEnvelope,
                chunks = chunks
            )
        } catch (_: Exception) {
            null
        }
    }
    
    data class ParamRequirements(
        val required: List<String> = emptyList(),
        val optionalWithDefaults: Map<String, Any> = emptyMap()
    )
    
    private fun getParamRequirements(tool: String): ParamRequirements {
        return when (tool) {
            "calculate_nutrition_metrics" -> ParamRequirements(
                required = listOf("age", "sex", "heightCm", "weightKg"),
                optionalWithDefaults = mapOf(
                    "activityLevel" to "moderate",
                    "goal" to "maintenance"
                )
            )
            "answer_with_retrieval" -> ParamRequirements(
                required = listOf("query"),
                optionalWithDefaults = mapOf(
                    "source" to DocumentRetrievalConfig.defaultSource,
                    "strategy" to DocumentRetrievalConfig.DEFAULT_STRATEGY,
                    "topK" to DocumentRetrievalConfig.DEFAULT_TOP_K,
                    "maxChars" to DocumentRetrievalConfig.DEFAULT_MAX_CHARS,
                    "perDocumentLimit" to DocumentRetrievalConfig.DEFAULT_PER_DOCUMENT_LIMIT
                )
            )
            else -> ParamRequirements()
        }
    }
    
    private fun completeParamsWithDefaults(
        tool: String,
        params: Map<String, Any?>
    ): Map<String, Any?> {
        val requirements = getParamRequirements(tool)
        val completed = params.toMutableMap()
        
        val usedDefaults = mutableListOf<String>()
        
        requirements.optionalWithDefaults.forEach { (key, defaultValue) ->
            if (!completed.containsKey(key)) {
                completed[key] = defaultValue
                usedDefaults.add("$key=$defaultValue")
            }
        }
        
        if (usedDefaults.isNotEmpty()) {
            Log.d(TAG, "🔧 Using default values for $tool: ${usedDefaults.joinToString(", ")}")
        }
        
        return completed
    }
}
