package com.example.aiadventchallenge.data.mcp

import android.util.Log
import com.example.aiadventchallenge.data.mcp.model.*
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionResult
import com.example.aiadventchallenge.domain.model.mcp.McpTool
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
    ): String {
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

        return response.result?.message ?: ""
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
