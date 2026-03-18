package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository

class AskWithPromptModeUseCase(private val repository: AiRepository) {
    suspend operator fun invoke(
        userInput: String,
        profile: UserProfile?,
        mode: PromptMode
    ): ChatResult<Answer> {
        return repository.askWithPromptMode(userInput, profile, mode)
    }
}
