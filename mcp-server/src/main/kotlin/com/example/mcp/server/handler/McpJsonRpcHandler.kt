package com.example.mcp.server.handler

import com.example.mcp.server.model.*
import com.example.mcp.server.model.nutrition.NutritionMetricsRequest
import com.example.mcp.server.model.nutrition.NutritionMetricsResponse
import com.example.mcp.server.model.meal.MealGuidanceRequest
import com.example.mcp.server.model.meal.MealGuidanceResponse
import com.example.mcp.server.model.training.TrainingGuidanceRequest
import com.example.mcp.server.model.training.TrainingGuidanceResponse
import com.example.mcp.server.service.nutrition.NutritionMetricsService
import com.example.mcp.server.service.meal.MealGuidanceService
import com.example.mcp.server.service.training.TrainingGuidanceService
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*

class McpJsonRpcHandler {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val nutritionMetricsService = NutritionMetricsService()
    private val mealGuidanceService = MealGuidanceService()
    private val trainingGuidanceService = TrainingGuidanceService()

    private val tools = listOf(
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
        ),
        Tool(
            name = "generate_meal_guidance",
            description = "Generates meal guidance based on nutrition metrics. Parameters: goal, targetCalories, proteinG, fatG, carbsG, mealsPerDay (optional, default 3), dietaryPreferences (optional), dietaryRestrictions (optional, default none). Returns mealStrategy, mealDistribution, recommendedFoods, foodsToLimit, notes."
        ),
        Tool(
            name = "generate_training_guidance",
            description = "Generates training plan. Parameters: goal, trainingLevel (optional, default beginner), trainingDaysPerWeek (optional, default 3), sessionDurationMinutes (optional, default 60), availableEquipment (optional, default gym), restrictions (optional, default none). Returns trainingSplit, weeklyPlan, exercisePrinciples, recoveryNotes, notes."
        )
    )

    fun handle(requestBody: String): String {
        return try {
            val request = json.decodeFromString<JsonRpcRequest>(requestBody)

            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleListTools(request)
                "ping" -> handlePing(request)
                "get_app_info" -> handleGetAppInfo(request)
                "calculate_nutrition_metrics" -> handleCalculateNutritionMetrics(request)
                "generate_meal_guidance" -> handleGenerateMealGuidance(request)
                "generate_training_guidance" -> handleGenerateTrainingGuidance(request)
                else -> handleUnknownMethod(request)
            }
        } catch (e: Exception) {
            val errorResponse = JsonRpcResponse(
                jsonrpc = "2.0",
                id = -1,
                result = null,
                error = JsonRpcError(
                    code = -32600,
                    message = "Invalid Request: ${e.message}"
                )
            )
            json.encodeToString(errorResponse)
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): String {
        println("   Method: initialize")

        val resultJson = buildJsonObject {
            put("message", "Initialized")
            put("serverInfo", buildJsonObject {
                put("name", "MCP Fitness Server")
                put("version", "2.0.0")
                put("platform", "Kotlin/JVM")
            })
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    private fun handleListTools(request: JsonRpcRequest): String {
        println("   Method: tools/list")

        val resultJson = buildJsonObject {
            put("tools", json.encodeToJsonElement(tools))
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    private fun handlePing(request: JsonRpcRequest): String {
        println("   Method: ping")

        val resultJson = buildJsonObject {
            put("message", "pong")
            put("timestamp", System.currentTimeMillis())
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    private fun handleGetAppInfo(request: JsonRpcRequest): String {
        println("   Method: get_app_info")

        val resultJson = buildJsonObject {
            put("name", "MCP Fitness Server")
            put("version", "2.0.0")
            put("platform", "Kotlin/JVM")
            put("build", Date().toString())
            put("tools", tools.size)
            put("description", "MCP server for fitness nutrition, meal and training guidance")
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    private fun handleCalculateNutritionMetrics(request: JsonRpcRequest): String {
        println("   Method: calculate_nutrition_metrics")

        return try {
            val params = request.params as? Map<String, Any?> ?: throw Exception("Invalid params")
            
            val nutritionRequest = NutritionMetricsRequest(
                sex = params["sex"] as? String ?: throw Exception("Missing sex parameter"),
                age = params["age"] as? Int ?: throw Exception("Missing age parameter"),
                heightCm = params["heightCm"] as? Int ?: throw Exception("Missing heightCm parameter"),
                weightKg = (params["weightKg"] as? Number)?.toDouble() ?: throw Exception("Missing weightKg parameter"),
                activityLevel = params["activityLevel"] as? String ?: throw Exception("Missing activityLevel parameter"),
                goal = params["goal"] as? String ?: throw Exception("Missing goal parameter")
            )

            val validationResult = nutritionRequest.validate()
            if (validationResult is com.example.mcp.server.model.nutrition.ValidationResult.Error) {
                throw Exception(validationResult.message)
            }

            val result = nutritionMetricsService.calculate(nutritionRequest)

            val resultJson = buildJsonObject {
                put("bmr", result.bmr)
                put("tdee", result.tdee)
                put("targetCalories", result.targetCalories)
                put("proteinG", result.proteinG)
                put("fatG", result.fatG)
                put("carbsG", result.carbsG)
                put("notes", result.notes)
            }

            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    private fun buildSuccessResponse(id: Int, resultData: JsonObject): String {
        return """{"jsonrpc":"2.0","id":$id,"result":${json.encodeToString(resultData)},"error":null}"""
    }

    private fun handleGenerateMealGuidance(request: JsonRpcRequest): String {
        println("   Method: generate_meal_guidance")

        return try {
            val params = request.params as? Map<String, Any?> ?: throw Exception("Invalid params")
            
            val mealRequest = MealGuidanceRequest(
                goal = params["goal"] as? String ?: throw Exception("Missing goal parameter"),
                targetCalories = params["targetCalories"] as? Int ?: throw Exception("Missing targetCalories parameter"),
                proteinG = params["proteinG"] as? Int ?: throw Exception("Missing proteinG parameter"),
                fatG = params["fatG"] as? Int ?: throw Exception("Missing fatG parameter"),
                carbsG = params["carbsG"] as? Int ?: throw Exception("Missing carbsG parameter"),
                mealsPerDay = params["mealsPerDay"] as? Int,
                dietaryPreferences = params["dietaryPreferences"] as? String,
                dietaryRestrictions = params["dietaryRestrictions"] as? String
            )

            val validationResult = mealRequest.validate()
            if (validationResult is com.example.mcp.server.model.meal.ValidationResult.Error) {
                throw Exception(validationResult.message)
            }

            val result = mealGuidanceService.generate(mealRequest)

            val resultJson = buildJsonObject {
                put("mealStrategy", result.mealStrategy)
                put("mealDistribution", json.encodeToJsonElement(result.mealDistribution))
                put("recommendedFoods", json.encodeToJsonElement(result.recommendedFoods))
                put("foodsToLimit", json.encodeToJsonElement(result.foodsToLimit))
                put("notes", result.notes)
            }

            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    private fun handleGenerateTrainingGuidance(request: JsonRpcRequest): String {
        println("   Method: generate_training_guidance")

        return try {
            val params = request.params as? Map<String, Any?> ?: throw Exception("Invalid params")
            
            val trainingRequest = TrainingGuidanceRequest(
                goal = params["goal"] as? String ?: throw Exception("Missing goal parameter"),
                trainingLevel = params["trainingLevel"] as? String,
                trainingDaysPerWeek = params["trainingDaysPerWeek"] as? Int,
                sessionDurationMinutes = params["sessionDurationMinutes"] as? Int,
                availableEquipment = (params["availableEquipment"] as? List<*>)?.filterIsInstance<String>(),
                restrictions = params["restrictions"] as? String
            )

            val validationResult = trainingRequest.validate()
            if (validationResult is com.example.mcp.server.model.training.ValidationResult.Error) {
                throw Exception(validationResult.message)
            }

            val result = trainingGuidanceService.generate(trainingRequest)

            val resultJson = buildJsonObject {
                put("trainingSplit", result.trainingSplit)
                put("weeklyPlan", json.encodeToJsonElement(result.weeklyPlan))
                put("exercisePrinciples", result.exercisePrinciples)
                put("recoveryNotes", result.recoveryNotes)
                put("notes", result.notes)
            }

            buildSuccessResponse(request.id, resultJson)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            buildErrorResponse(request.id, e)
        }
    }

    private fun handleUnknownMethod(request: JsonRpcRequest): String {
        return """{"jsonrpc":"2.0","id":${request.id},"result":null,"error":{"code":-32601,"message":"Method not found: ${request.method}"}}"""
    }

    private fun buildErrorResponse(id: Any?, error: Exception): String {
        return """{"jsonrpc":"2.0","id":$id,"result":null,"error":{"code":-32603,"message":"${error.message ?: "Unknown error"}"}}"""
    }
}
