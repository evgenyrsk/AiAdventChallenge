package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PromptMode
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.UserProfile
import com.example.aiadventchallenge.domain.repository.AiRepository

class AskWithPromptModeUseCase(private val repository: AiRepository) {
    private val answerCache = mutableMapOf<PromptMode, Answer>()

    suspend operator fun invoke(
        userInput: String,
        profile: UserProfile?,
        mode: PromptMode
    ): ChatResult<Answer> {
        val systemPrompt = Prompts.getPromptModeSystemPrompt(mode)
        val config = RequestConfig(systemPrompt = systemPrompt)
        return when (val result = repository.askWithUsage(userInput, profile, config, RequestType.CHAT)) {
            is ChatResult.Success -> ChatResult.Success(Answer(content = result.data.content))
            is ChatResult.Error -> result
        }
    }

    suspend fun askAllModes(
        userInput: String,
        profile: UserProfile?,
        onProgress: (PromptMode, Boolean) -> Unit = { _, _ -> }
    ): Map<PromptMode, Answer> {
        val results = mutableMapOf<PromptMode, Answer>()

        PromptMode.entries.forEach { mode ->
            onProgress(mode, true)
            val result = invoke(userInput, profile, mode)
            if (result is ChatResult.Success) {
                results[mode] = result.data
                answerCache[mode] = result.data
            }
            onProgress(mode, false)
        }

        return results
    }

    fun saveAnswer(mode: PromptMode, answer: Answer) {
        answerCache[mode] = answer
    }

    fun getLatestAnswer(mode: PromptMode): Answer? {
        return answerCache[mode]
    }
}
