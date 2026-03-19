package com.example.aiadventchallenge.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("response_format")
    val responseFormat: ResponseFormat? = null,
    val stop: List<String>? = null,
    val reasoning: ReasoningConfig? = null
)

@Serializable
data class ReasoningConfig(
    val effort: String? = null,
    val exclude: Boolean? = null,
    val enabled: Boolean? = null
)

@Serializable
data class ResponseFormat(
    val type: String,
)
