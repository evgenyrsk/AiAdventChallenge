package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.repository.AiRepository

class TemperatureUseCase(private val repository: AiRepository) {

    suspend operator fun invoke(userInput: String, temperature: Double): ChatResult<Answer> {
        val config = RequestConfig(
            systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT,
            temperature = temperature,
            reasoningEnabled = false
        )
        return when (val result = repository.askWithUsage(userInput = userInput, profile = null, config = config, requestType = RequestType.CHAT)) {
            is ChatResult.Success -> ChatResult.Success(Answer(content = result.data.content))
            is ChatResult.Error -> result
        }
    }
}
