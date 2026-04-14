package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.data.mcp.model.JsonRpcRequest
import com.example.aiadventchallenge.data.mcp.model.JsonRpcResponse
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse
import com.example.aiadventchallenge.domain.model.mcp.MealGuidanceResponse
import com.example.aiadventchallenge.domain.model.mcp.TrainingGuidanceResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class McpJsonRpcClient(
    private val serverUrl: String
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private var requestId = 0

    suspend fun initialize(): String {
        val request = JsonRpcRequest(
            id = ++requestId,
            method = "initialize",
            params = null
        )

        val responseJson = sendRequest(request)

        val response = json.decodeFromString<JsonRpcResponse>(responseJson)

        if (response.error != null) {
            throw McpException("Initialize failed: ${response.error.message}")
        }

        return response.result?.message ?: "Initialized"
    }

    suspend fun listTools(): List<McpTool> {
        val request = JsonRpcRequest(
            id = ++requestId,
            method = "tools/list",
            params = null
        )

        val responseJson = sendRequest(request)

        val response = json.decodeFromString<JsonRpcResponse>(responseJson)

        if (response.error != null) {
            throw McpException("List tools failed: ${response.error.message}")
        }

        return response.result?.tools?.map { tool ->
            McpTool(
                name = tool.name,
                description = tool.description
            )
        } ?: emptyList()
    }

    suspend fun callTool(
        name: String,
        params: Map<String, Any?>
    ): McpToolData {
        val jsonParams = params.mapValues { (_, value) -> toJsonElement(value) }

        val request = JsonRpcRequest(
            id = ++requestId,
            method = name,
            params = jsonParams
        )

        val responseJson = sendRequest(request)

        val response = json.decodeFromString<JsonRpcResponse>(responseJson)

        if (response.error != null) {
            throw McpException("Tool call failed: ${response.error.message}")
        }

        if (response.result == null) {
            return McpToolData.StringResult("")
        }

        return when (name) {
            "calculate_nutrition_metrics" -> {
                val resultElement = json.parseToJsonElement(responseJson).jsonObject["result"]?.jsonObject
                if (resultElement != null) {
                    val bmr = resultElement["bmr"]?.toString()?.toIntOrNull() ?: 0
                    val tdee = resultElement["tdee"]?.toString()?.toIntOrNull() ?: 0
                    val targetCalories = resultElement["targetCalories"]?.toString()?.toIntOrNull() ?: 0
                    val proteinG = resultElement["proteinG"]?.toString()?.toIntOrNull() ?: 0
                    val fatG = resultElement["fatG"]?.toString()?.toIntOrNull() ?: 0
                    val carbsG = resultElement["carbsG"]?.toString()?.toIntOrNull() ?: 0
                    val notes = resultElement["notes"]?.toString() ?: ""
                    
                    McpToolData.NutritionMetrics(
                        NutritionMetricsResponse(bmr, tdee, targetCalories, proteinG, fatG, carbsG, notes)
                    )
                } else {
                    McpToolData.StringResult("")
                }
            }
            "generate_meal_guidance" -> {
                val resultElement = json.parseToJsonElement(responseJson).jsonObject["result"]?.jsonObject
                if (resultElement != null) {
                    val mealStrategy = resultElement["mealStrategy"]?.toString() ?: ""
                    val recommendedFoods = (resultElement["recommendedFoods"] as? kotlinx.serialization.json.JsonArray)?.map { it.toString() } ?: emptyList()
                    val foodsToLimit = (resultElement["foodsToLimit"] as? kotlinx.serialization.json.JsonArray)?.map { it.toString() } ?: emptyList()
                    val notes = resultElement["notes"]?.toString() ?: ""
                    val mealDistributionJson = resultElement["mealDistribution"] as? kotlinx.serialization.json.JsonArray
                    
                    val mealDistribution = mealDistributionJson?.map { mealJson ->
                        val meal = mealJson.jsonObject
                        com.example.aiadventchallenge.domain.model.mcp.MealSuggestion(
                            meal = meal["meal"]?.toString()?.toIntOrNull() ?: 1,
                            calories = meal["calories"]?.toString()?.toIntOrNull() ?: 0,
                            proteinG = meal["proteinG"]?.toString()?.toIntOrNull() ?: 0,
                            suggestions = meal["suggestions"]?.toString() ?: ""
                        )
                    } ?: emptyList()
                    
                    McpToolData.MealGuidance(
                        MealGuidanceResponse(mealStrategy, mealDistribution, recommendedFoods, foodsToLimit, notes)
                    )
                } else {
                    McpToolData.StringResult("")
                }
            }
            "generate_training_guidance" -> {
                val resultElement = json.parseToJsonElement(responseJson).jsonObject["result"]?.jsonObject
                if (resultElement != null) {
                    val trainingSplit = resultElement["trainingSplit"]?.toString() ?: ""
                    val exercisePrinciples = resultElement["exercisePrinciples"]?.toString() ?: ""
                    val recoveryNotes = resultElement["recoveryNotes"]?.toString() ?: ""
                    val notes = resultElement["notes"]?.toString() ?: ""
                    val weeklyPlanJson = resultElement["weeklyPlan"] as? kotlinx.serialization.json.JsonArray
                    
                    val weeklyPlan = weeklyPlanJson?.map { dayJson ->
                        val day = dayJson.jsonObject
                        val exercisesJson = day["exercises"] as? kotlinx.serialization.json.JsonArray
                        val exercises = exercisesJson?.map { exerciseJson ->
                            val exercise = exerciseJson.jsonObject
                            com.example.aiadventchallenge.domain.model.mcp.Exercise(
                                name = exercise["name"]?.toString() ?: "",
                                sets = exercise["sets"]?.toString()?.toIntOrNull() ?: 0,
                                reps = exercise["reps"]?.toString() ?: ""
                            )
                        } ?: emptyList()
                        
                        com.example.aiadventchallenge.domain.model.mcp.TrainingDay(
                            day = day["day"]?.toString()?.toIntOrNull() ?: 1,
                            focus = day["focus"]?.toString() ?: "",
                            exercises = exercises
                        )
                    } ?: emptyList()
                    
                    McpToolData.TrainingGuidance(
                        TrainingGuidanceResponse(trainingSplit, weeklyPlan, exercisePrinciples, recoveryNotes, notes)
                    )
                } else {
                    McpToolData.StringResult("")
                }
            }
            else -> {
                val rawResult = json.parseToJsonElement(responseJson).jsonObject["result"]
                McpToolData.StringResult(rawResult?.toString() ?: response.result.message.orEmpty())
            }
        }
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (key, nestedValue) ->
                key.toString() to toJsonElement(nestedValue)
            }
        )
        is Iterable<*> -> JsonArray(value.map { toJsonElement(it) })
        is Array<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private suspend fun sendRequest(request: JsonRpcRequest): String = suspendCancellableCoroutine { continuation ->
        try {
            val requestBody = json.encodeToString(request)
            val body = requestBody.toRequestBody("application/json".toMediaType())

            Log.d(TAG, "📤 Sending MCP Request: $requestBody")

            val httpRequest = Request.Builder()
                .url(serverUrl)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            val call = client.newCall(httpRequest)

            continuation.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) {
                        Log.e(TAG, "❌ MCP Request failed", e)
                        continuation.resumeWithException(McpException("Connection failed: ${e.message}"))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { httpResponse ->
                        val responseBody = httpResponse.body?.string() ?: ""

                        Log.d(TAG, "📥 MCP Response: $responseBody")

                        if (!continuation.isCancelled) {
                            if (httpResponse.isSuccessful) {
                                continuation.resume(responseBody)
                            } else {
                                continuation.resumeWithException(
                                    McpException("HTTP ${httpResponse.code}: $responseBody")
                                )
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            if (!continuation.isCancelled) {
                Log.e(TAG, "❌ MCP Request error", e)
                continuation.resumeWithException(e)
            }
        }
    }

    class McpException(message: String) : Exception(message)

    companion object {
        private const val TAG = "McpJsonRpcClient"
    }
}
