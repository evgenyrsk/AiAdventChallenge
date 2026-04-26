package com.example.aiadventchallenge.domain.model

data class PrivateAiServiceConfig(
    val baseUrl: String = "http://10.0.2.2:8085",
    val apiKey: String = "",
    val model: String = "qwen2.5:3b-instruct",
    val timeoutMs: Long = 120_000L,
    val maxTokens: Int? = null,
    val contextWindow: Int? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val repeatPenalty: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null
)
