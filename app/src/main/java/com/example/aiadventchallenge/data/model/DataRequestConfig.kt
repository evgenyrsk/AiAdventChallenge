package com.example.aiadventchallenge.data.model

data class DataRequestConfig(
    val systemPrompt: String,
    val modelId: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val reasoning: ReasoningConfig? = null
)
