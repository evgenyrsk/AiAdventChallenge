package com.example.aiadventchallenge.domain.model

enum class ConversationMode {
    FITNESS,
    DEVELOPER_HELP
}

data class ChatMessage(
    val id: String,
    val parentMessageId: String?,
    val content: String,
    val isFromUser: Boolean,
    val isSystemMessage: Boolean = false,
    val branchId: String = "main",
    val conversationMode: ConversationMode = ConversationMode.FITNESS,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val isHidden: Boolean = false
)
