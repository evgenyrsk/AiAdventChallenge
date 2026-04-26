package com.example.aiadventchallenge.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PrivateAiServiceChatRequest(
    val messages: List<Message>,
    val model: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val contextWindow: Int? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val repeatPenalty: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class PrivateAiServiceChatResponse(
    val message: ResponseMessage,
    val model: String,
    val usage: PrivateAiServiceUsage,
    val metrics: PrivateAiServiceMetrics
)

@Serializable
data class PrivateAiServiceUsage(
    val inputChars: Int,
    val outputChars: Int
)

@Serializable
data class PrivateAiServiceMetrics(
    val latencyMs: Long
)

@Serializable
data class PrivateAiServiceErrorResponse(
    val error: String? = null,
    val code: Int? = null
)
