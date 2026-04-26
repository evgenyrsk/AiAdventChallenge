package com.example.privateaiservice.api.model

import kotlinx.serialization.Serializable

@Serializable
data class GatewayChatRequest(
    val messages: List<GatewayChatMessage>,
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
data class GatewayChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class GatewayChatResponse(
    val message: GatewayChatMessage,
    val model: String,
    val usage: GatewayUsage,
    val metrics: GatewayMetrics
)

@Serializable
data class GatewayUsage(
    val inputChars: Int,
    val outputChars: Int
)

@Serializable
data class GatewayMetrics(
    val latencyMs: Long
)

@Serializable
data class GatewayErrorResponse(
    val error: String,
    val code: Int
)

@Serializable
data class HealthResponse(
    val status: String,
    val ollamaAvailable: Boolean,
    val model: String,
    val ollamaBaseUrl: String
)
