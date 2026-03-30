package com.example.aiadventchallenge.data.agent

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.data.mapper.MessageMapper
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.agent.Agent
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.CompressedChatHistory
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.usecase.AskAiUseCase
import com.example.aiadventchallenge.domain.usecase.AskMode

class ChatAgent(
    private val askAiUseCase: AskAiUseCase,
    private val repository: AiRepository
) : Agent {

    override suspend fun processRequest(
        userInput: String,
        profile: UserProfile?
    ): ChatResult<String> {
        return when (val result = askAiUseCase(userInput, AskMode.WITHOUT_LIMITS, profile)) {
            is ChatResult.Success -> ChatResult.Success(result.data.content)
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    override suspend fun processRequestWithContext(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<String> {
        return when (val result = repository.askWithContext(messages, config)) {
            is ChatResult.Success -> ChatResult.Success(result.data.content)
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    suspend fun processRequestWithContextAndUsage(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage> {
        return when (val result = repository.askWithContext(messages, config, RequestType.CHAT)) {
            is ChatResult.Success -> ChatResult.Success(result.data)
            is ChatResult.Error -> ChatResult.Error(result.message, result.code)
        }
    }

    fun buildRequestConfig(): RequestConfig {
        return RequestConfig(
            systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT,
        )
    }
}
