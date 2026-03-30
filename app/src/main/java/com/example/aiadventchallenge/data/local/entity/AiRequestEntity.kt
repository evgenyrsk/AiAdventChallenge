package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_requests")
data class AiRequestEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    val requestType: String,
    val model: String?,
    val prompt: String?,
    val response: String?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)
