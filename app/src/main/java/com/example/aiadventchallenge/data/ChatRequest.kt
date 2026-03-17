package com.example.aiadventchallenge.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String
)