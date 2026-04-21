package com.example.aiadventchallenge.data.repository

import android.util.Log
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.config.JsonConfig
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.OllamaChatRequest
import com.example.aiadventchallenge.data.model.OllamaChatResponse
import com.example.aiadventchallenge.data.model.OllamaErrorResponse
import com.example.aiadventchallenge.data.model.OllamaMessage
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * HTTP client for a local Ollama instance.
 * Receives already prepared chat context from the app and only adapts it to Ollama API.
 */
class LocalOllamaRepository(
    private val httpClient: HttpClient,
    private val aiRequestRepository: AiRequestRepository
) : AiRepository {

    private val ollamaJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    override suspend fun ask(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig
    ): ChatResult<Answer> {
        return when (val result = askWithUsage(userInput, profile, config)) {
            is ChatResult.Success -> ChatResult.Success(Answer(result.data.content))
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    override suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Local Ollama backend requires explicit settings")
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Local Ollama backend requires explicit settings")
    }

    override suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Local Ollama backend requires explicit settings")
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Local Ollama backend requires explicit settings")
    }

    suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig,
        requestType: RequestType,
        backendSettings: AiBackendSettings
    ): ChatResult<AnswerWithUsage> {
        if (messages.isEmpty()) {
            return ChatResult.Error("Список сообщений не может быть пустым")
        }

        val localConfig = backendSettings.localConfig
        val baseUrl = normalizeBaseUrl(localConfig.host, localConfig.port)
        val model = config.modelId ?: localConfig.model
        val request = OllamaChatRequest(
            model = model,
            messages = messages.map(::toOllamaMessage),
            stream = false,
            options = com.example.aiadventchallenge.data.model.OllamaOptions(
                temperature = config.temperature,
                numPredict = config.maxTokens,
                stop = config.stop
            )
        )

        Log.d(TAG, "Using LOCAL_OLLAMA backend: endpoint=$baseUrl/api/chat, model=$model, stream=false")

        val requestJson = ollamaJson.encodeToString(request)
        return httpClient.post(
            url = "$baseUrl/api/chat",
            requestJson = requestJson,
            headers = mapOf("Content-Type" to "application/json")
        ).fold(
            onSuccess = { responseBody ->
                val parsed = try {
                    parseResponse(responseBody)
                } catch (error: IllegalStateException) {
                    return@fold ChatResult.Error(error.message ?: "Получен ответ в неожиданном формате от Ollama.")
                }
                if (parsed.content.isBlank()) {
                    return@fold ChatResult.Error("Локальная модель вернула пустой ответ.")
                }

                aiRequestRepository.recordRequest(
                    type = requestType,
                    model = "LOCAL_OLLAMA/$model",
                    prompt = buildPromptText(messages),
                    response = parsed.content,
                    promptTokens = parsed.promptTokens,
                    completionTokens = parsed.completionTokens,
                    totalTokens = parsed.totalTokens
                )

                Log.d(TAG, "LOCAL_OLLAMA success: model=$model")
                ChatResult.Success(parsed)
            },
            onFailure = { error ->
                val message = mapError(error)
                Log.e(TAG, "LOCAL_OLLAMA error: ${error.message}", error)
                ChatResult.Error(message, (error as? HttpClient.HttpException)?.code)
            }
        )
    }

    internal fun normalizeBaseUrl(host: String, port: Int): String {
        val trimmedHost = host.trim().ifBlank { "10.0.2.2" }
        val normalizedHost = when (trimmedHost.lowercase()) {
            "localhost", "127.0.0.1" -> "10.0.2.2"
            else -> trimmedHost
        }
        return "http://$normalizedHost:$port"
    }

    private fun toOllamaMessage(message: Message): OllamaMessage {
        return OllamaMessage(
            role = message.role,
            content = message.content
        )
    }

    private fun parseResponse(responseBody: String): AnswerWithUsage {
        runCatching {
            val parsed = ollamaJson.decodeFromString(OllamaChatResponse.serializer(), responseBody)
            return toAnswerWithUsage(parsed)
        }

        return try {
            parseNdjsonResponse(responseBody)
        } catch (error: Exception) {
            throw IllegalStateException("Получен ответ в неожиданном формате от Ollama.", error)
        }
    }

    private fun parseNdjsonResponse(responseBody: String): AnswerWithUsage {
        val lines = responseBody
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.isEmpty()) {
            throw IllegalStateException("Получен пустой потоковый ответ от Ollama.")
        }

        Log.d(TAG, "Detected NDJSON streaming response from Ollama, aggregating chunks: count=${lines.size}")

        val chunks = lines.map { line ->
            ollamaJson.decodeFromString(OllamaChatResponse.serializer(), line)
        }

        val finalChunk = chunks.lastOrNull { it.done == true } ?: chunks.last()
        val content = buildString {
            chunks.forEach { chunk ->
                append(chunk.message?.content.orEmpty())
            }
        }.trim()

        if (content.isBlank()) {
            throw IllegalStateException("Локальная модель вернула пустой потоковый ответ.")
        }

        Log.d(TAG, "Ollama response mode=ndjson_stream, finalLength=${content.length}")

        return AnswerWithUsage(
            content = content,
            promptTokens = finalChunk.promptEvalCount,
            completionTokens = finalChunk.evalCount,
            totalTokens = listOfNotNull(finalChunk.promptEvalCount, finalChunk.evalCount).sum().takeIf { it > 0 }
        )
    }

    private fun toAnswerWithUsage(parsed: OllamaChatResponse): AnswerWithUsage {
        val content = parsed.message?.content?.trim().orEmpty()
        Log.d(TAG, "Ollama response mode=single_json, finalLength=${content.length}")
        return AnswerWithUsage(
            content = content,
            promptTokens = parsed.promptEvalCount,
            completionTokens = parsed.evalCount,
            totalTokens = listOfNotNull(parsed.promptEvalCount, parsed.evalCount).sum().takeIf { it > 0 }
        )
    }

    private fun mapError(error: Throwable): String {
        return when (error) {
            is IllegalStateException -> "Получен ответ в неожиданном формате от Ollama."
            is ConnectException -> "Не удалось подключиться к локальной модели. Проверьте, что Ollama запущена на Mac и доступна по указанному адресу."
            is SocketTimeoutException -> "Локальная модель не ответила вовремя."
            is UnknownHostException -> "Хост локальной модели недоступен."
            is HttpClient.HttpException -> mapHttpError(error)
            is IOException -> "Хост локальной модели недоступен."
            else -> error.message ?: "Не удалось выполнить запрос к локальной модели."
        }
    }

    private fun mapHttpError(error: HttpClient.HttpException): String {
        val responseError = runCatching {
            JsonConfig.json.decodeFromString(OllamaErrorResponse.serializer(), error.body).error
        }.getOrNull()

        val message = responseError ?: error.body
        val modelName = extractModelName(message)
        return when {
            error.code == 404 && message.contains("model", ignoreCase = true) ->
                "Модель '$modelName' не найдена в Ollama. Укажите точное имя из 'ollama list', например 'qwen2.5:3b-instruct'."
            error.code == 404 ->
                "Сервер Ollama недоступен по указанному endpoint."
            message.contains("model", ignoreCase = true) &&
                message.contains("not found", ignoreCase = true) ->
                "Модель '$modelName' не найдена в Ollama. Укажите точное имя из 'ollama list', например 'qwen2.5:3b-instruct'."
            message.contains("connection refused", ignoreCase = true) ->
                "Не удалось подключиться к локальной модели. Проверьте, что Ollama запущена на Mac и доступна по указанному адресу."
            else -> "Не удалось выполнить запрос к локальной модели."
        }
    }

    private fun extractModelName(message: String): String {
        val match = Regex("""model\s+'([^']+)'""", RegexOption.IGNORE_CASE).find(message)
        return match?.groupValues?.getOrNull(1) ?: "unknown"
    }

    private fun buildPromptText(messages: List<Message>): String {
        return messages.joinToString("\n\n") { message ->
            "${message.role}: ${message.content}"
        }
    }

    private companion object {
        const val TAG = "LocalOllamaRepository"
    }
}
