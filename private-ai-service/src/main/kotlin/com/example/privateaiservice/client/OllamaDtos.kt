package com.example.privateaiservice.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("num_ctx")
    val numCtx: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("repeat_penalty")
    val repeatPenalty: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaMessage? = null,
    val error: String? = null
)

@Serializable
data class OllamaTagsResponse(
    val models: List<OllamaTagModel> = emptyList()
)

@Serializable
data class OllamaTagModel(
    val name: String
)
