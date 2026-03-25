package com.example.aiadventchallenge.domain.model

data class DialogTokenStats(
    val requestsCount: Int = 0,
    val totalPromptTokens: Int = 0,
    val totalCompletionTokens: Int = 0,
    val totalTokens: Int = 0
)