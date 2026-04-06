package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "chat_messages",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["branchId"], name = "index_chat_messages_branchId"),
        Index(value = ["parentMessageId"], name = "index_chat_messages_parentMessageId"),
        Index(value = ["timestamp"], name = "index_chat_messages_timestamp"),
        Index(value = ["isHidden"], name = "index_chat_messages_isHidden")
    ]
)
data class ChatMessageEntity(
    val id: String,
    val parentMessageId: String?,
    val content: String,
    val isFromUser: Boolean,
    val isSystemMessage: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),

    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val branchId: String = "main",
    val isHidden: Boolean = false
)