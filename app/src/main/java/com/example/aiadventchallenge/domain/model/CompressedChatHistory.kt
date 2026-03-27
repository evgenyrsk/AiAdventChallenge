package com.example.aiadventchallenge.domain.model

data class CompressedChatHistory(
    val summaries: List<SummaryMessage>,
    val recentMessages: List<ChatMessage>
)
