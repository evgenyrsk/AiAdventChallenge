package com.example.aiadventchallenge.domain.model

data class ChatMessage(
    val id: String,
    val parentMessageId: String?,
    val content: String,
    val isFromUser: Boolean,
    val branchId: String = "main",
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)