package com.example.aiadventchallenge.data.repository

import android.util.Log
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.config.JsonConfig
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.PrivateAiServiceChatRequest
import com.example.aiadventchallenge.data.model.PrivateAiServiceChatResponse
import com.example.aiadventchallenge.data.model.PrivateAiServiceErrorResponse
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PrivateAiServiceRepository(
    private val httpClient: HttpClient,
    private val aiRequestRepository: AiRequestRepository
) : AiRepository {

    override suspend fun ask(userInput: String, profile: UserProfile?, config: RequestConfig): ChatResult<Answer> {
        return when (val result = askWithUsage(userInput, profile, config)) {
            is ChatResult.Success -> ChatResult.Success(Answer(result.data.content))
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    override suspend fun askWithContext(messages: List<Message>, config: RequestConfig): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Private AI service backend requires explicit settings")
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Private AI service backend requires explicit settings")
    }

    override suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Private AI service backend requires explicit settings")
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage> {
        return ChatResult.Error("Private AI service backend requires explicit settings")
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

        val privateConfig = backendSettings.privateServiceConfig
        val model = config.modelId ?: privateConfig.model
        val timeoutMs = privateConfig.timeoutMs
        val request = PrivateAiServiceChatRequest(
            messages = messages,
            model = model,
            temperature = config.temperature,
            maxTokens = config.maxTokens ?: privateConfig.maxTokens,
            contextWindow = config.numCtx ?: privateConfig.contextWindow,
            topP = config.topP ?: privateConfig.topP,
            topK = config.topK ?: privateConfig.topK,
            repeatPenalty = config.repeatPenalty ?: privateConfig.repeatPenalty,
            seed = config.seed ?: privateConfig.seed,
            stop = config.stop ?: privateConfig.stop
        )

        val sanitizedBaseUrl = privateConfig.baseUrl.trim().trimEnd('/')
        Log.d(TAG, "Using PRIVATE_AI_SERVICE backend: baseUrl=$sanitizedBaseUrl, model=$model, timeoutMs=$timeoutMs")

        return httpClient.post(
            url = "$sanitizedBaseUrl/v1/chat",
            requestJson = JsonConfig.json.encodeToString(request),
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${privateConfig.apiKey}"
            ),
            timeoutMs = timeoutMs
        ).fold(
            onSuccess = { responseBody ->
                val response = JsonConfig.json.decodeFromString(PrivateAiServiceChatResponse.serializer(), responseBody)
                val content = response.message.content?.trim().orEmpty()
                if (content.isBlank()) {
                    return@fold ChatResult.Error("Private AI service вернул пустой ответ.")
                }
                val answer = AnswerWithUsage(
                    content = content,
                    promptTokens = null,
                    completionTokens = null,
                    totalTokens = null
                )
                aiRequestRepository.recordRequest(
                    type = requestType,
                    model = "PRIVATE_AI_SERVICE/${response.model}",
                    prompt = buildPromptText(messages),
                    response = content,
                    promptTokens = null,
                    completionTokens = null,
                    totalTokens = null
                )
                ChatResult.Success(answer)
            },
            onFailure = { error ->
                Log.e(TAG, "PRIVATE_AI_SERVICE error: ${error.message}", error)
                ChatResult.Error(mapError(error), (error as? HttpClient.HttpException)?.code)
            }
        )
    }

    private fun mapError(error: Throwable): String {
        return when (error) {
            is ConnectException -> "Private AI service недоступен. Проверьте, что gateway запущен и доступен по указанному адресу."
            is SocketTimeoutException -> "Private AI service не ответил вовремя."
            is UnknownHostException -> "Хост private AI service недоступен."
            is HttpClient.HttpException -> mapHttpError(error)
            is IOException -> "Не удалось подключиться к private AI service."
            else -> error.message ?: "Не удалось выполнить запрос к private AI service."
        }
    }

    private fun mapHttpError(error: HttpClient.HttpException): String {
        val parsed = runCatching {
            JsonConfig.json.decodeFromString(PrivateAiServiceErrorResponse.serializer(), error.body)
        }.getOrNull()
        val message = parsed?.error?.ifBlank { null } ?: error.body
        return when (error.code) {
            400 -> "Private AI service отклонил запрос: $message"
            401 -> "Неверный API key для private AI service."
            429 -> "Превышен rate limit private AI service. Попробуйте чуть позже."
            502 -> "Private AI service не смог обратиться к Ollama: $message"
            504 -> "Ollama не ответила вовремя через private AI service."
            else -> "Private AI service вернул ошибку HTTP ${error.code}: $message"
        }
    }

    private fun buildPromptText(messages: List<Message>): String {
        return messages.joinToString("\n") { "${it.role}: ${it.content}" }
    }

    private companion object {
        const val TAG = "PrivateAiServiceRepo"
    }
}
