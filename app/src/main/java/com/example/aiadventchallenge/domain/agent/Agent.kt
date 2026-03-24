package com.example.aiadventchallenge.domain.agent

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.UserProfile

interface Agent {
    suspend fun processRequest(
        userInput: String,
        profile: UserProfile? = null
    ): ChatResult<String>

    suspend fun processRequestWithContext(
        messages: List<Message>,
        config: com.example.aiadventchallenge.domain.model.RequestConfig
    ): ChatResult<String>
}
