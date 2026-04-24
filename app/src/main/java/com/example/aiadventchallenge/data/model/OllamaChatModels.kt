package com.example.aiadventchallenge.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null,
    @SerialName("keep_alive")
    val keepAlive: String? = null
)

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    @SerialName("num_predict")
    val numPredict: Int? = null,
    @SerialName("num_ctx")
    val numCtx: Int? = null,
    @SerialName("top_k")
    val topK: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("repeat_penalty")
    val repeatPenalty: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null
)

@Serializable
data class OllamaMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatResponse(
    val model: String? = null,
    val message: OllamaMessage? = null,
    val done: Boolean? = null,
    @SerialName("done_reason")
    val doneReason: String? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
    val error: String? = null
)

@Serializable
data class OllamaErrorResponse(
    val error: String? = null
)
