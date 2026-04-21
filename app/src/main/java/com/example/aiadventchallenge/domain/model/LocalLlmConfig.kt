package com.example.aiadventchallenge.domain.model

data class LocalLlmConfig(
    val host: String = "10.0.2.2",
    val port: Int = 11434,
    val model: String = "qwen2.5:3b-instruct"
)
