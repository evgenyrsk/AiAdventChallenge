package com.example.mcp.server.handler

import com.example.mcp.server.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class McpJsonRpcHandler {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val tools = listOf(
        Tool(
            name = "ping",
            description = "Simple ping tool to test MCP connection. Returns 'pong' message."
        ),
        Tool(
            name = "get_app_info",
            description = "Returns information about the application including version, platform, and build details."
        ),
        Tool(
            name = "calculate_nutrition_plan",
            description = "Calculates daily calorie and macronutrient needs based on user parameters using Mifflin-St Jeor equation. Parameters: sex (male/female), age (years), heightCm (cm), weightKg (kg), activityLevel (sedentary/light/moderate/active/very_active), goal (weight_loss/maintenance/muscle_gain). Returns calories, protein/fat/carbs in grams and explanation."
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
                "calculate_nutrition_plan" -> handleCalculateNutritionPlan(request)
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

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = "MCP Server initialized successfully"
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleListTools(request: JsonRpcRequest): String {
        println("   Method: tools/list")
        println("   Returning ${tools.size} tools")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                tools = tools
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handlePing(request: JsonRpcRequest): String {
        println("   Method: ping")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = "pong"
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleGetAppInfo(request: JsonRpcRequest): String {
        println("   Method: get_app_info")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = """
                    App Info:
                    - Name: AiAdventChallenge MCP Test Server
                    - Version: 1.0.0
                    - Platform: JVM
                    - Status: Running
                """.trimIndent()
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleCalculateNutritionPlan(request: JsonRpcRequest): String {
        println("   Method: calculate_nutrition_plan")

        return try {
            val params = request.params ?: throw Exception("Parameters are required")

            val sex = params["sex"]?.toString() ?: throw Exception("Missing parameter: sex")
            val age = params["age"]?.toString()?.toIntOrNull() ?: throw Exception("Missing or invalid parameter: age")
            val heightCm = params["heightCm"]?.toString()?.toDoubleOrNull() ?: throw Exception("Missing or invalid parameter: heightCm")
            val weightKg = params["weightKg"]?.toString()?.toDoubleOrNull() ?: throw Exception("Missing or invalid parameter: weightKg")
            val activityLevel = params["activityLevel"]?.toString() ?: throw Exception("Missing parameter: activityLevel")
            val goal = params["goal"]?.toString() ?: throw Exception("Missing parameter: goal")

            println("   Parameters: sex=$sex, age=$age, height=$heightCm, weight=$weightKg, activity=$activityLevel, goal=$goal")

            val result = calculateNutrition(sex, age, heightCm, weightKg, activityLevel, goal)

            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = JsonRpcResult(
                    message = """
                        Nutrition Plan:
                        - Calories: ${result.calories} kcal
                        - Protein: ${result.proteinGrams} g
                        - Fat: ${result.fatGrams} g
                        - Carbs: ${result.carbsGrams} g

                        ${result.explanation}
                    """.trimIndent(),
                    nutritionResult = result
                ),
                error = null
            )

            json.encodeToString(response)
        } catch (e: Exception) {
            println("   Error: ${e.message}")
            val response = JsonRpcResponse(
                jsonrpc = "2.0",
                id = request.id,
                result = null,
                error = JsonRpcError(
                    code = -32602,
                    message = "Invalid params: ${e.message}"
                )
            )
            json.encodeToString(response)
        }
    }

    private fun calculateNutrition(
        sex: String,
        age: Int,
        heightCm: Double,
        weightKg: Double,
        activityLevel: String,
        goal: String
    ): CalculateNutritionResult {
        val isMale = sex.equals("male", ignoreCase = true)

        val bmr = if (isMale) {
            10 * weightKg + 6.25 * heightCm - 5 * age + 5
        } else {
            10 * weightKg + 6.25 * heightCm - 5 * age - 161
        }

        val activityMultiplier = when (activityLevel.lowercase()) {
            "sedentary" -> 1.2
            "light" -> 1.375
            "moderate" -> 1.55
            "active" -> 1.725
            "very_active" -> 1.9
            else -> 1.55
        }

        var tdee = bmr * activityMultiplier

        when (goal.lowercase()) {
            "weight_loss" -> tdee -= 500
            "muscle_gain" -> tdee += 300
        }

        tdee = maxOf(1200.0, tdee)

        val proteinMultiplier = when (goal.lowercase()) {
            "muscle_gain" -> 0.3
            "weight_loss" -> 0.35
            else -> 0.3
        }
        val fatMultiplier = 0.3
        val carbMultiplier = 1.0 - proteinMultiplier - fatMultiplier

        val proteinCalories = tdee * proteinMultiplier
        val fatCalories = tdee * fatMultiplier
        val carbCalories = tdee * carbMultiplier

        val proteinGrams = (proteinCalories / 4).toInt()
        val fatGrams = (fatCalories / 9).toInt()
        val carbsGrams = (carbCalories / 4).toInt()

        val explanation = buildString {
            append("Calculated using Mifflin-St Jeor equation with $activityLevel activity level and $goal goal. ")
            append("BMR: ${bmr.toInt()} kcal, TDEE: ${(bmr * activityMultiplier).toInt()} kcal.")
        }

        return CalculateNutritionResult(
            calories = tdee.toInt(),
            proteinGrams = proteinGrams,
            fatGrams = fatGrams,
            carbsGrams = carbsGrams,
            explanation = explanation
        )
    }

    private fun handleUnknownMethod(request: JsonRpcRequest): String {
        println("   Method: ${request.method} (unknown)")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = null,
            error = JsonRpcError(
                code = -32601,
                message = "Method not found: ${request.method}"
            )
        )

        return json.encodeToString(response)
    }
}
