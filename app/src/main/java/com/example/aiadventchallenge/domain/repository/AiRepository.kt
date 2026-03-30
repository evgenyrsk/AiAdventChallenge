package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.model.Answer
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.model.UserProfile

interface AiRepository {
    suspend fun ask(userInput: String, profile: UserProfile? = null, config: RequestConfig): ChatResult<Answer>
    suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage>
    suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile? = null,
        config: RequestConfig
    ): ChatResult<AnswerWithUsage>

    suspend fun askWithContext(
        messages: List<Message>,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage>
    suspend fun askWithUsage(
        userInput: String,
        profile: UserProfile?,
        config: RequestConfig,
        requestType: RequestType
    ): ChatResult<AnswerWithUsage>
}
