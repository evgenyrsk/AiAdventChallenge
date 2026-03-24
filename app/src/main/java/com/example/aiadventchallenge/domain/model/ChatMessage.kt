package com.example.aiadventchallenge.domain.model

data class ChatMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean
)
