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

    suspend fun ask(prompt: String): String = suspendCancellableCoroutine { continuation ->

        val requestDto = ChatRequest(
            model = "google/gemma-3-4b-it:free",
            messages = listOf(
                Message(
                    role = "user",
                    content = prompt,
                )
            )
        )

        val bodyJson = json.encodeToString(ChatRequest.serializer(), requestDto)

        val body = bodyJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://example.com")
            .addHeader("X-Title", "AiAdventChallenge")
            .post(body)
            .build()

        val call = client.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
        }

        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resume("Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val responseBody = it.body?.string().orEmpty()
                    Log.d("OPENROUTER", responseBody)

                    val resultText = if (!it.isSuccessful) {
                        try {
                            val errorResponse = json.decodeFromString<ErrorResponse>(responseBody)
                            "Error: ${errorResponse.error?.message ?: "Unknown API error"}"
                        } catch (_: Exception) {
                            "Error: HTTP ${it.code}: $responseBody"
                        }
                    } else {
                        try {
                            val parsedResponse = json.decodeFromString<ChatResponse>(responseBody)
                            parsedResponse.choices
                                .firstOrNull()
                                ?.message
                                ?.content
                                ?: "Empty response"
                        } catch (e: Exception) {
                            "Error parsing response: ${e.message}"
                        }
                    }

                    if (!continuation.isCancelled) {
                        continuation.resume(resultText)
                    }
                }
            }
        })
    }
}