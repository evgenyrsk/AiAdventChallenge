package com.example.privateaiservice

import com.example.privateaiservice.api.model.GatewayChatMessage
import com.example.privateaiservice.api.model.GatewayChatRequest
import com.example.privateaiservice.api.model.GatewayChatResponse
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

fun main() {
    val baseUrl = System.getenv("PRIVATE_AI_SERVICE_BASE_URL") ?: "http://localhost:8085"
    val apiKey = System.getenv("PRIVATE_AI_API_KEY") ?: ""
    val model = System.getenv("PRIVATE_AI_SERVICE_MODEL") ?: "qwen2.5:3b-instruct"
    val iterations = System.getenv("PRIVATE_AI_SMOKE_ITERATIONS")?.toIntOrNull() ?: 3
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var successCount = 0
    repeat(iterations) { index ->
        val requestBody = GatewayChatRequest(
            messages = listOf(
                GatewayChatMessage(role = "user", content = "Reply with a short greeting #${index + 1}")
            ),
            model = model,
            maxTokens = 64,
            contextWindow = 1024,
            temperature = 0.2
        )
        val startedAt = System.currentTimeMillis()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1/chat")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(json.encodeToString(GatewayChatRequest.serializer(), requestBody).toRequestBody(JSON))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val latencyMs = System.currentTimeMillis() - startedAt
                val body = response.body.string()
                if (!response.isSuccessful) {
                    println("[$index] failure status=${response.code} latencyMs=$latencyMs error=$body")
                    return@repeat
                }
                val parsed = json.decodeFromString(GatewayChatResponse.serializer(), body)
                successCount += 1
                println("[$index] success latencyMs=$latencyMs outputChars=${parsed.usage.outputChars}")
            }
        } catch (error: Exception) {
            println("[$index] failure status=EXCEPTION latencyMs=${System.currentTimeMillis() - startedAt} error=${error.message}")
        }
    }

    check(successCount == iterations) {
        "Smoke check failed: $successCount/$iterations requests succeeded"
    }
}

private val JSON = "application/json".toMediaType()
