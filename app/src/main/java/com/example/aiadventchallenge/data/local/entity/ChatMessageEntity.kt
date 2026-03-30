package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "chat_messages",
    primaryKeys = ["id", "branchId"],
    indices = [
        Index(value = ["branchId"], name = "index_chat_messages_branchId"),
        Index(value = ["timestamp"], name = "index_chat_messages_timestamp")
    ]
)
data class ChatMessageEntity(
    val id: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),

    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val branchId: String = "main"
)