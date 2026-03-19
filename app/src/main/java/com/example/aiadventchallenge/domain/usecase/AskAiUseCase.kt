package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.data.model.RequestConfig
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository

enum class AskMode {
    WITH_LIMITS,
    WITHOUT_LIMITS
}

class AskAiUseCase(private val repository: AiRepository) {
    private val answerCache = mutableMapOf<AskMode, Answer>()

    suspend operator fun invoke(
        userInput: String,
        mode: AskMode,
        profile: UserProfile?
    ): ChatResult<Answer> {
        val config = when (mode) {
            AskMode.WITH_LIMITS -> RequestConfig(
                systemPrompt = Prompts.LIMITED_SYSTEM_PROMPT,
                maxTokens = 60,
                stop = listOf("END")
            )
            AskMode.WITHOUT_LIMITS -> RequestConfig(
                systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT
            )
        }
        return repository.ask(userInput, profile, config)
    }

    fun saveAnswer(mode: AskMode, answer: Answer) {
        answerCache[mode] = answer
    }

    fun getLatestAnswer(mode: AskMode): Answer? {
        return answerCache[mode]
    }
}
