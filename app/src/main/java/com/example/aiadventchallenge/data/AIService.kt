package com.example.aiadventchallenge.data

import android.util.Log
import com.example.aiadventchallenge.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class AIService {

    private val client = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun askWithNoLimits(
        messages: List<Message>,
    ): String {
        val request = ChatRequest(
            model = MODEL,
            messages = messages
        )
        return ask(request)
    }

    suspend fun askWithLimits(
        messages: List<Message>,
    ): String {
        val request = ChatRequest(
            model = MODEL,
            messages = messages,
            responseFormat = ResponseFormat(type = "json_object"),
            maxTokens = 120,
            stop = listOf("END"),
            reasoning = ReasoningConfig(
                effort = "none",
                exclude = true
            ),
        )

        return ask(request)
    }

    private suspend fun ask(
        chatRequest: ChatRequest
    ): String = suspendCancellableCoroutine { continuation ->

        val requestJson = json.encodeToString(ChatRequest.serializer(), chatRequest)
        Log.d(TAG, "Request: $requestJson")

        val body = requestJson.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(BASE_URL)
            .addHeader("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://example.com")
            .addHeader("X-Title", "AiAdventChallenge")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resume("Error: ${e.message ?: "Network error"}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { httpResponse ->
                    val responseBody = httpResponse.body?.string().orEmpty()
                    Log.d(TAG, "Response: $responseBody")

                    val resultText = if (!httpResponse.isSuccessful) {
                        parseError(httpResponse.code, responseBody)
                    } else {
                        parseSuccess(responseBody)
                    }

                    if (!continuation.isCancelled) {
                        continuation.resume(resultText)
                    }
                }
            }
        })
    }

    private fun parseSuccess(responseBody: String): String {
        return try {
            val parsed = json.decodeFromString(ChatResponse.serializer(), responseBody)
            val choice = parsed.choices.firstOrNull()
            val message = choice?.message

            when {
                !message?.content.isNullOrBlank() -> {
                    message?.content!!.trim()
                }
                !message?.reasoning.isNullOrBlank() -> {
                    "Model returned reasoning but no final answer. " +
                            "finish_reason=${choice?.finishReason}, reasoning=${message?.reasoning}"
                }
                else -> {
                    "Empty response"
                }
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message ?: "Unknown parsing error"}"
        }
    }

    private fun parseError(code: Int, responseBody: String): String {
        return try {
            val errorResponse = json.decodeFromString(ErrorResponse.serializer(), responseBody)
            "Error: ${errorResponse.error?.message ?: "HTTP $code"}"
        } catch (_: Exception) {
            "Error: HTTP $code: $responseBody"
        }
    }

    companion object {
        private const val MODEL = "nvidia/nemotron-3-super-120b-a12b:free"
        private const val TAG = "OPENROUTER"
        private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    }
}