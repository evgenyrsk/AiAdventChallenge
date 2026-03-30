package com.example.aiadventchallenge.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean,
    val branchId: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)