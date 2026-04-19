package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_task_state")
data class ConversationTaskStateEntity(
    @PrimaryKey
    val branchId: String,
    val payloadJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
