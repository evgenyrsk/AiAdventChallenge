package com.example.aiadventchallenge.domain.model

data class RequestConfig(
    val systemPrompt: String = "",
    val modelId: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val reasoningEnabled: Boolean = false,
)
