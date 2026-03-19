package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.data.model.ReasoningConfig
import com.example.aiadventchallenge.data.model.RequestConfig
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.repository.AiRepository

class TemperatureUseCase(private val repository: AiRepository) {

    suspend operator fun invoke(userInput: String, temperature: Double): ChatResult<Answer> {
        val config = RequestConfig(
            systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT,
            temperature = temperature,
            reasoning = ReasoningConfig(exclude = true),
        )
        return repository.ask(userInput = userInput, config = config)
    }
}