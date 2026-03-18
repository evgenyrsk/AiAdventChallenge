package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.config.JsonConfig
import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.data.api.ApiConfig
import com.example.aiadventchallenge.data.model.ChatRequest
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.model.ReasoningConfig
import com.example.aiadventchallenge.data.parser.ResponseParser
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import kotlinx.serialization.encodeToString

class AiRepositoryImpl(
    private val httpClient: HttpClient,
    private val config: ApiConfig,
    private val responseParser: ResponseParser
) : AiRepository {

    override suspend fun askWithLimits(userInput: String, profile: UserProfile?): ChatResult<Answer> {
        if (userInput.isBlank()) {
            return ChatResult.Error("Запрос не может быть пустым")
        }

        val messages = buildLimitedMessages(userInput, profile)
        val request = ChatRequest(
            model = config.model,
            messages = messages,
            maxTokens = 60,
            stop = listOf("END"),
            //reasoning = ReasoningConfig(effort = "none", exclude = true)
        )

        return executeRequest(request)
    }

    override suspend fun askWithoutLimits(userInput: String, profile: UserProfile?): ChatResult<Answer> {
        if (userInput.isBlank()) {
            return ChatResult.Error("Запрос не может быть пустым")
        }

        val messages = buildUnlimitedMessages(userInput, profile)
        val request = ChatRequest(
            model = config.model,
            messages = messages
        )

        return executeRequest(request)
    }

    override suspend fun askWithPromptMode(
        userInput: String,
        profile: UserProfile?,
        mode: PromptMode
    ): ChatResult<Answer> {
        if (userInput.isBlank()) {
            return ChatResult.Error("Запрос не может быть пустым")
        }

        val messages = buildPromptModeMessages(userInput, profile, mode)
        val request = ChatRequest(
            model = config.model,
            messages = messages,
            //reasoning = ReasoningConfig(effort = "none", exclude = true),
        )

        return executeRequest(request)
    }

    private fun buildLimitedMessages(userInput: String, profile: UserProfile?): List<Message> {
        val systemMessage = Message(MessageRole.SYSTEM, Prompts.LIMITED_SYSTEM_PROMPT)
        val profileSection = buildProfileSection(profile)
        val userContent = if (profileSection.isNotEmpty()) {
            "$profileSection\n\n$userInput"
        } else {
            userInput
        }
        return listOf(systemMessage, Message(MessageRole.USER, userContent))
    }

    private fun buildUnlimitedMessages(userInput: String, profile: UserProfile?): List<Message> {
        val systemMessage = Message(MessageRole.SYSTEM, Prompts.UNLIMITED_SYSTEM_PROMPT)
        val profileSection = buildProfileSection(profile)
        val userContent = if (profileSection.isNotEmpty()) {
            "$profileSection\n\n$userInput"
        } else {
            userInput
        }
        return listOf(systemMessage, Message(MessageRole.USER, userContent))
    }

    private fun buildPromptModeMessages(
        userInput: String,
        profile: UserProfile?,
        mode: PromptMode
    ): List<Message> {
        val systemPrompt = Prompts.getPromptModeSystemPrompt(mode)
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
}
