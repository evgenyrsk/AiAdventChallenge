package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository

enum class AskMode {
    WITH_LIMITS,
    WITHOUT_LIMITS
}

class AskAiUseCase(private val repository: AiRepository) {
    suspend operator fun invoke(
        userInput: String,
        mode: AskMode,
        profile: UserProfile?
    ): ChatResult<Answer> {
        return when (mode) {
            AskMode.WITH_LIMITS -> repository.askWithLimits(userInput, profile)
            AskMode.WITHOUT_LIMITS -> repository.askWithoutLimits(userInput, profile)
        }
    }
}
