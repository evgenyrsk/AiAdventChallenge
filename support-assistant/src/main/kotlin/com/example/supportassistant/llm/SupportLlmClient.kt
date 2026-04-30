package com.example.supportassistant.llm

import com.example.supportassistant.config.SupportConfig
import com.example.supportassistant.service.SupportLlmUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class LlmResult(
    val content: String,
    val latencyMs: Long
)

interface SupportLlmClient {
    suspend fun generate(prompt: String): LlmResult
}

class HttpSupportLlmClient(
    private val config: SupportConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.llmTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.llmTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.llmTimeoutMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()
) : SupportLlmClient {

    override suspend fun generate(prompt: String): LlmResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val request = when (config.llmBackend.lowercase()) {
            "private" -> privateAiRequest(prompt)
            "ollama" -> ollamaRequest(prompt)
            "cloud" -> cloudRequest(prompt)
            else -> throw SupportLlmUnavailableException("Unsupported support LLM backend: ${config.llmBackend}")
        }
        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw SupportLlmUnavailableException("LLM backend returned HTTP ${response.code}")
                }
                val content = parseResponse(body).trim()
                if (content.isBlank()) {
                    throw SupportLlmUnavailableException("LLM backend returned an empty answer")
                }
                LlmResult(content = content, latencyMs = System.currentTimeMillis() - startedAt)
            }
        } catch (timeout: SocketTimeoutException) {
            throw SupportLlmUnavailableException("LLM backend timed out")
        } catch (io: IOException) {
            throw SupportLlmUnavailableException("LLM backend is unavailable")
        }
    }

    private fun privateAiRequest(prompt: String): Request {
        val body = GatewayChatRequest(
            messages = listOf(GatewayChatMessage("user", prompt)),
            model = config.llmModel,
            temperature = 0.2,
            maxTokens = 700
        )
        return Request.Builder()
            .url("${config.llmBaseUrl.trimEnd('/')}/v1/chat")
            .post(json.encodeToString(GatewayChatRequest.serializer(), body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .apply {
                if (config.llmApiKey.isNotBlank()) header("Authorization", "Bearer ${config.llmApiKey}")
            }
            .build()
    }

    private fun ollamaRequest(prompt: String): Request {
        val body = OllamaChatRequest(
            model = config.llmModel,
            messages = listOf(GatewayChatMessage("user", prompt)),
            stream = false
        )
        return Request.Builder()
            .url("${config.llmBaseUrl.trimEnd('/')}/api/chat")
            .post(json.encodeToString(OllamaChatRequest.serializer(), body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
    }

    private fun cloudRequest(prompt: String): Request {
        val body = CloudChatRequest(
            model = config.llmModel,
            messages = listOf(GatewayChatMessage("user", prompt)),
            temperature = 0.2
        )
        return Request.Builder()
            .url(config.llmBaseUrl)
            .post(json.encodeToString(CloudChatRequest.serializer(), body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .apply {
                if (config.llmApiKey.isNotBlank()) header("Authorization", "Bearer ${config.llmApiKey}")
            }
            .build()
    }

    private fun parseResponse(body: String): String {
        return when (config.llmBackend.lowercase()) {
            "private" -> json.decodeFromString(GatewayChatResponse.serializer(), body).message.content
            "ollama" -> json.decodeFromString(OllamaChatResponse.serializer(), body).message.content
            "cloud" -> json.decodeFromString(CloudChatResponse.serializer(), body).choices.firstOrNull()?.message?.content.orEmpty()
            else -> ""
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

@Serializable
private data class GatewayChatRequest(
    val messages: List<GatewayChatMessage>,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)

@Serializable
private data class GatewayChatMessage(
    val role: String,
    val content: String
)

@Serializable
private data class GatewayChatResponse(
    val message: GatewayChatMessage,
    val model: String
)

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val messages: List<GatewayChatMessage>,
    val stream: Boolean = false
)

@Serializable
private data class OllamaChatResponse(
    val message: GatewayChatMessage
)

@Serializable
private data class CloudChatRequest(
    val model: String,
    val messages: List<GatewayChatMessage>,
    val temperature: Double? = null
)

@Serializable
private data class CloudChatResponse(
    val choices: List<CloudChoice> = emptyList()
)

@Serializable
private data class CloudChoice(
    val message: GatewayChatMessage
)
