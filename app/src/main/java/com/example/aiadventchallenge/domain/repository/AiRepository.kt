package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.data.model.RequestConfig
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.UserProfile

interface AiRepository {
    suspend fun ask(userInput: String, profile: UserProfile? = null, config: RequestConfig): ChatResult<Answer>
}
