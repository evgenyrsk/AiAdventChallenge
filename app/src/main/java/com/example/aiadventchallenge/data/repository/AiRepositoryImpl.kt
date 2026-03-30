package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.config.JsonConfig
import com.example.aiadventchallenge.data.api.ApiConfig
import com.example.aiadventchallenge.data.model.ChatRequest
import com.example.aiadventchallenge.data.model.DataRequestConfig
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.model.ReasoningConfig
import com.example.aiadventchallenge.data.parser.ResponseParser
import com.example.aiadventchallenge.data.parser.ResponseParserWithUsage
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig as DomainRequestConfig
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import kotlinx.serialization.encodeToString

class AiRepositoryImpl(
    private val httpClient: HttpClient,
    private val config: ApiConfig,
    private val responseParser: ResponseParser
) : AiRepository {
    private val responseParserWithUsage = ResponseParserWithUsage()

    override suspend fun ask(
        userInput: String,
        profile: UserProfile?,
        config: DomainRequestConfig
    ): ChatResult<Answer> {
        if (userInput.isBlank()) {
            return ChatResult.Error("Запрос не может быть пустым")
        }

        val messages = buildMessages(userInput, profile, config.systemPrompt)
        val dataConfig = toDataRequestConfig(config)
        val request = ChatRequest(
            model = dataConfig.modelId ?: this.config.model,
            messages = messages,
            temperature = dataConfig.temperature,
            maxTokens = dataConfig.maxTokens,
            stop = dataConfig.stop,
            reasoning = dataConfig.reasoning
        )

        return executeRequest(request)
    }

    override suspend fun askWithContext(
        messages: List<Message>,
        config: DomainRequestConfig
    ): ChatResult<AnswerWithUsage> {
        if (messages.isEmpty()) {
            return ChatResult.Error("Список сообщений не может быть пустым")
        }

        val dataConfig = toDataRequestConfig(config)
        val request = ChatRequest(
            model = dataConfig.modelId ?: this.config.model,
            messages = messages,
            temperature = dataConfig.temperature,
            maxTokens = dataConfig.maxTokens,
            stop = dataConfig.stop,
            reasoning = dataConfig.reasoning
        )

        return executeRequestWithUsage(request)
    }

    override suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: DomainRequestConfig
    ): ChatResult<AnswerWithUsage> {
        if (userInput.isBlank()) {
            return ChatResult.Error("Запрос не может быть пустым")
        }

        val messages = buildMessages(userInput, profile, config.systemPrompt)
        val dataConfig = toDataRequestConfig(config)
        val request = ChatRequest(
            model = dataConfig.modelId ?: this.config.model,
            messages = messages,
            temperature = dataConfig.temperature,
            maxTokens = dataConfig.maxTokens,
            stop = dataConfig.stop,
            reasoning = dataConfig.reasoning
        )

        return executeRequestWithUsage(request)
    }

    private fun toDataRequestConfig(domainConfig: DomainRequestConfig): DataRequestConfig {
        return DataRequestConfig(
            systemPrompt = domainConfig.systemPrompt,
            modelId = domainConfig.modelId,
            temperature = domainConfig.temperature,
            maxTokens = domainConfig.maxTokens,
            stop = domainConfig.stop,
            reasoning = if (!domainConfig.reasoningEnabled) {
                ReasoningConfig(
                    exclude = true,
                    effort = "none",
                    enabled = false,
                )
            } else {
                ReasoningConfig(exclude = true)
            }
        )
    }

    private fun buildMessages(userInput: String, profile: UserProfile?, systemPrompt: String): List<Message> {
        val systemMessage = Message(MessageRole.SYSTEM, systemPrompt)
        val profileSection = buildProfileSection(profile)
        val userContent = if (profileSection.isNotEmpty()) {
            "$profileSection\n\n$userInput"
        } else {
            userInput
        }
        return listOf(systemMessage, Message(MessageRole.USER, userContent))
    }

    private fun buildProfileSection(profile: UserProfile?): String {
        if (profile == null || profile.isEmpty()) return ""

        return buildString {
            appendLine("Параметры пользователя:")
            profile.age?.let { appendLine("- Возраст: $it лет") }
            profile.weight?.let { appendLine("- Вес: $it кг") }
            profile.height?.let { appendLine("- Рост: $it см") }
            profile.goal?.let { appendLine("- Цель: ${it.label}") }
            profile.activityLevel?.let { appendLine("- Активность: ${it.label}") }
        }.trim()
    }

    private suspend fun executeRequest(request: ChatRequest): ChatResult<Answer> {
        val requestJson = JsonConfig.json.encodeToString(request)

        return httpClient.post(requestJson).fold(
            onSuccess = { responseBody ->
                val parsedContent = responseParser.parse(responseBody)
                ChatResult.Success(Answer(content = parsedContent))
            },
            onFailure = { error ->
                when (error) {
                    is HttpClient.HttpException -> {
                        val errorMessage = responseParser.parseError(error.code, error.body)
                        ChatResult.Error(errorMessage, error.code)
                    }
                    else -> ChatResult.Error(error.message ?: "Network error")
                }
            }
        )
    }

    private suspend fun executeRequestWithUsage(request: ChatRequest): ChatResult<AnswerWithUsage> {
        val requestJson = JsonConfig.json.encodeToString(request)

        return httpClient.post(requestJson).fold(
            onSuccess = { responseBody ->
                val parsed = responseParserWithUsage.parse(responseBody)
                ChatResult.Success(AnswerWithUsage(
                    content = parsed.content,
                    promptTokens = parsed.promptTokens,
                    completionTokens = parsed.completionTokens,
                    totalTokens = parsed.totalTokens
                ))
            },
            onFailure = { error ->
                when (error) {
                    is HttpClient.HttpException -> {
                        val errorMessage = responseParserWithUsage.parseError(error.code, error.body)
                        ChatResult.Error(errorMessage, error.code)
                    }
                    else -> ChatResult.Error(error.message ?: "Network error")
                }
            }
        )
    }
}
