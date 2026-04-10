package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.data.mcp.model.*
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpTool
import com.example.aiadventchallenge.domain.mcp.McpToolData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val jsonParams = params.mapValues { (_, value) ->
            when (value) {
                is String -> kotlinx.serialization.json.JsonPrimitive(value)
                is Number -> kotlinx.serialization.json.JsonPrimitive(value)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
            }
        }

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

        val result = response.result ?: return McpToolData.StringResult("")

        return when {
            result.fitnessSummaryResult != null -> {
                val s = result.fitnessSummaryResult
                McpToolData.FitnessSummary(
                    com.example.aiadventchallenge.domain.mcp.FitnessSummaryData(
                        period = s.period,
                        entriesCount = s.entriesCount,
                        avgWeight = s.avgWeight,
                        workoutsCompleted = s.workoutsCompleted,
                        avgSteps = s.avgSteps,
                        avgSleepHours = s.avgSleepHours,
                        avgProtein = s.avgProtein,
                        adherenceScore = s.adherenceScore,
                        summaryText = s.summaryText
                    )
                )
            }
            result.scheduledSummaryResult != null -> {
                val s = result.scheduledSummaryResult
                McpToolData.ScheduledSummary(
                    com.example.aiadventchallenge.domain.mcp.ScheduledSummaryData(
                        id = s.id,
                        period = s.period,
                        entriesCount = s.entriesCount,
                        avgWeight = s.avgWeight,
                        workoutsCompleted = s.workoutsCompleted,
                        avgSteps = s.avgSteps,
                        avgSleepHours = s.avgSleepHours,
                        avgProtein = s.avgProtein,
                        adherenceScore = s.adherenceScore,
                        summaryText = s.summaryText,
                        createdAt = s.createdAt
                    )
                )
            }
            result.addFitnessLogResult != null -> {
                val r = result.addFitnessLogResult
                McpToolData.AddFitnessLog(
                    com.example.aiadventchallenge.domain.mcp.AddFitnessLogData(
                        success = r.success,
                        id = r.id,
                        message = r.message
                    )
                )
            }
            result.runScheduledSummaryResult != null -> {
                val r = result.runScheduledSummaryResult
                McpToolData.RunScheduledSummary(
                    com.example.aiadventchallenge.domain.mcp.RunScheduledSummaryData(
                        success = r.success,
                        summaryId = r.summaryId,
                        message = r.message
                    )
                )
            }
            result.fitnessSummaryExportFullResponse != null -> {
                val r = result.fitnessSummaryExportFullResponse
                val summary = r.summaryData.summaryResult
                McpToolData.ExportResult(
                    com.example.aiadventchallenge.domain.mcp.ExportData(
                        filePath = r.exportResult.filePath,
                        format = r.exportResult.format,
                        savedAt = r.exportResult.savedAt,
                        errorMessage = r.exportResult.errorMessage,
                        summaryData = summary?.let {
                            com.example.aiadventchallenge.domain.mcp.ExportSummaryData(
                                period = it.period,
                                entriesCount = it.entriesCount,
                                avgWeight = it.avgWeight,
                                workoutsCompleted = it.workoutsCompleted,
                                avgSteps = it.avgSteps,
                                avgSleepHours = it.avgSleepHours,
                                avgProtein = it.avgProtein
                            )
                        }
                    )
                )
            }
            else -> McpToolData.StringResult(result.message ?: "")
        }
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
