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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.util.*

abstract class AbstractMcpJsonRpcHandler {
    protected val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    protected val nutritionMetricsService by lazy { NutritionMetricsService() }
    protected val mealGuidanceService by lazy { MealGuidanceService() }
    protected val trainingGuidanceService by lazy { TrainingGuidanceService() }

    abstract val tools: List<Tool>

    protected abstract fun getServerInfo(): String

    protected open fun handleInitialize(request: JsonRpcRequest): String {
        println("   Method: initialize")

        val resultJson = buildJsonObject {
            put("message", "Initialized")
            put("serverInfo", buildJsonObject {
                put("name", getServerInfo())
                put("version", "2.0.0")
                put("platform", "Kotlin/JVM")
            })
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handleListTools(request: JsonRpcRequest): String {
        println("   Method: tools/list")

        val resultJson = buildJsonObject {
            put("tools", json.encodeToJsonElement(tools))
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handlePing(request: JsonRpcRequest): String {
        println("   Method: ping")

        val resultJson = buildJsonObject {
            put("message", "pong")
            put("timestamp", System.currentTimeMillis())
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handleGetAppInfo(request: JsonRpcRequest): String {
        println("   Method: get_app_info")

        val resultJson = buildJsonObject {
            put("name", getServerInfo())
            put("version", "2.0.0")
            put("platform", "Kotlin/JVM")
            put("build", java.util.Date().toString())
            put("tools", tools.size)
            put("description", "MCP server for fitness nutrition, meal and training guidance")
        }

        return buildSuccessResponse(request.id, resultJson)
    }

    protected open fun handleCalculateNutritionMetrics(request: JsonRpcRequest): String {
        println("   Method: calculate_nutrition_metrics")

        return try {
            val paramsElement = request.params ?: throw Exception("Missing params")

            val sex = paramsElement["sex"]?.jsonPrimitive?.content
                ?: throw Exception("Missing sex parameter")
            val age = paramsElement["age"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing age parameter")
            val heightCm = paramsElement["heightCm"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing heightCm parameter")
            val weightKg = paramsElement["weightKg"]?.jsonPrimitive?.content?.toDouble()
                ?: throw Exception("Missing weightKg parameter")
            val activityLevel = paramsElement["activityLevel"]?.jsonPrimitive?.content
                ?: throw Exception("Missing activityLevel parameter")
            val goal = paramsElement["goal"]?.jsonPrimitive?.content
                ?: throw Exception("Missing goal parameter")

            val nutritionRequest = NutritionMetricsRequest(
                sex = sex,
                age = age,
                heightCm = heightCm,
                weightKg = weightKg,
                activityLevel = activityLevel,
                goal = goal
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

    protected open fun handleGenerateMealGuidance(request: JsonRpcRequest): String {
        println("   Method: generate_meal_guidance")

        return try {
            val paramsElement = request.params ?: throw Exception("Missing params")

            val goal = paramsElement["goal"]?.jsonPrimitive?.content
                ?: throw Exception("Missing goal parameter")
            val targetCalories = paramsElement["targetCalories"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing targetCalories parameter")
            val proteinG = paramsElement["proteinG"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing proteinG parameter")
            val fatG = paramsElement["fatG"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing fatG parameter")
            val carbsG = paramsElement["carbsG"]?.jsonPrimitive?.content?.toInt()
                ?: throw Exception("Missing carbsG parameter")

            val mealRequest = MealGuidanceRequest(
                goal = goal,
                targetCalories = targetCalories,
                proteinG = proteinG,
                fatG = fatG,
                carbsG = carbsG,
                mealsPerDay = paramsElement["mealsPerDay"]?.jsonPrimitive?.content?.toInt(),
                dietaryPreferences = paramsElement["dietaryPreferences"]?.jsonPrimitive?.content,
                dietaryRestrictions = paramsElement["dietaryRestrictions"]?.jsonPrimitive?.content
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

    protected open fun handleGenerateTrainingGuidance(request: JsonRpcRequest): String {
        println("   Method: generate_training_guidance")

        return try {
            val paramsElement = request.params ?: throw Exception("Missing params")

            val goal = paramsElement["goal"]?.jsonPrimitive?.content
                ?: throw Exception("Missing goal parameter")

            val trainingRequest = TrainingGuidanceRequest(
                goal = goal,
                trainingLevel = paramsElement["trainingLevel"]?.jsonPrimitive?.content,
                trainingDaysPerWeek = paramsElement["trainingDaysPerWeek"]?.jsonPrimitive?.content?.toInt(),
                sessionDurationMinutes = paramsElement["sessionDurationMinutes"]?.jsonPrimitive?.content?.toInt(),
                availableEquipment = (paramsElement["availableEquipment"] as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content },
                restrictions = paramsElement["restrictions"]?.jsonPrimitive?.content
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

    protected open fun handleUnknownMethod(request: JsonRpcRequest): String {
        return """{"jsonrpc":"2.0","id":${request.id},"result":null,"error":{"code":-32601,"message":"Method not found: ${request.method}"}}"""
    }

    protected fun buildSuccessResponse(id: Int, resultData: JsonObject): String {
        return """{"jsonrpc":"2.0","id":$id,"result":${json.encodeToString(resultData)},"error":null}"""
    }

    protected fun buildErrorResponse(id: Any?, error: Exception): String {
        return """{"jsonrpc":"2.0","id":$id,"result":null,"error":{"code":-32603,"message":"${error.message ?: "Unknown error"}"}}"""
    }

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
}

class McpJsonRpcHandler : AbstractMcpJsonRpcHandler() {
    override val tools = listOf(
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

    override fun getServerInfo(): String = "MCP Fitness Server"
}
