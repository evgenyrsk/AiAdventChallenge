package com.example.privateaiservice.service

import com.example.privateaiservice.api.model.GatewayChatMessage
import com.example.privateaiservice.api.model.GatewayChatRequest
import com.example.privateaiservice.client.OllamaChatRequest
import com.example.privateaiservice.client.OllamaChatResponse
import com.example.privateaiservice.client.OllamaMessage
import com.example.privateaiservice.client.OllamaOptions
import com.example.privateaiservice.client.OllamaTagsResponse
import com.example.privateaiservice.config.PrivateAiServiceConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class OllamaGatewayResult(
    val message: GatewayChatMessage,
    val model: String,
    val latencyMs: Long
)

data class OllamaHealthResult(
    val available: Boolean,
    val model: String
)

open class OllamaGatewayService(
    private val config: PrivateAiServiceConfig,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    open fun chat(request: GatewayChatRequest): OllamaGatewayResult {
        val startedAt = System.currentTimeMillis()
        val targetModel = request.model?.takeIf { it.isNotBlank() } ?: config.defaultModel
        val upstreamRequest = OllamaChatRequest(
            model = targetModel,
            messages = request.messages.map { OllamaMessage(role = it.role, content = it.content) },
            options = OllamaOptions(
                temperature = request.temperature,
                numPredict = request.maxTokens,
                numCtx = request.contextWindow,
                topP = request.topP,
                topK = request.topK,
                repeatPenalty = request.repeatPenalty,
                seed = request.seed,
                stop = request.stop
            )
        )

        val httpRequest = Request.Builder()
            .url("${config.ollamaBaseUrl.trimEnd('/')}/api/chat")
            .post(json.encodeToString(OllamaChatRequest.serializer(), upstreamRequest).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()

        return try {
            httpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    throw mapHttpError(response.code, body)
                }
                val parsed = runCatching {
                    json.decodeFromString(OllamaChatResponse.serializer(), body)
                }.getOrElse { throw UpstreamUnavailableException("Ollama returned malformed response") }

                val content = parsed.message?.content?.trim().orEmpty()
                if (content.isBlank()) {
                    throw UpstreamUnavailableException("Ollama returned an empty response")
                }

                OllamaGatewayResult(
                    message = GatewayChatMessage(role = parsed.message?.role ?: "assistant", content = content),
                    model = parsed.model ?: targetModel,
                    latencyMs = System.currentTimeMillis() - startedAt
                )
            }
        } catch (timeout: SocketTimeoutException) {
            throw UpstreamTimeoutException("Ollama timed out")
        } catch (io: IOException) {
            throw UpstreamUnavailableException("Ollama is unavailable")
        }
    }

    open fun health(): OllamaHealthResult {
        val request = Request.Builder()
            .url("${config.ollamaBaseUrl.trimEnd('/')}/api/tags")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return OllamaHealthResult(available = false, model = config.defaultModel)
                }
                val parsed = runCatching {
                    json.decodeFromString(OllamaTagsResponse.serializer(), response.body.string())
                }.getOrNull()
                val model = parsed?.models?.firstOrNull { it.name == config.defaultModel }?.name
                    ?: parsed?.models?.firstOrNull()?.name
                    ?: config.defaultModel
                OllamaHealthResult(available = true, model = model)
            }
        } catch (_: Exception) {
            OllamaHealthResult(available = false, model = config.defaultModel)
        }
    }

    private fun mapHttpError(code: Int, body: String): ServiceException {
        return when {
            code == 404 && body.contains("model", ignoreCase = true) ->
                UpstreamUnavailableException("Requested model is unavailable in Ollama")
            else -> UpstreamUnavailableException("Ollama request failed with HTTP $code")
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
