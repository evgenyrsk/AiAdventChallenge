package com.example.aiadventchallenge.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int? = null,
    val message: ResponseMessage? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
data class ErrorResponse(
    val error: ApiError? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val code: Int? = null,
    val metadata: ErrorMetadata? = null
)

@Serializable
data class ErrorMetadata(
    @SerialName("raw")
    val raw: String? = null
)
