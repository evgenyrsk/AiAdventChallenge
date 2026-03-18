package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.UserProfile

interface AiRepository {
    suspend fun askWithLimits(userInput: String, profile: UserProfile?): ChatResult<Answer>
    suspend fun askWithoutLimits(userInput: String, profile: UserProfile?): ChatResult<Answer>
}
