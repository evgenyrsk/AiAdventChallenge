package com.example.aiadventchallenge.domain.model

data class RequestConfig(
    val systemPrompt: String = "",
    val modelId: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val numCtx: Int? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val repeatPenalty: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    val keepAlive: String? = null,
    val promptProfile: PromptProfile = PromptProfile.BASELINE,
    val localLlmProfile: LocalLlmProfile = LocalLlmProfile.BASELINE,
    val reasoningEnabled: Boolean = false,
)
