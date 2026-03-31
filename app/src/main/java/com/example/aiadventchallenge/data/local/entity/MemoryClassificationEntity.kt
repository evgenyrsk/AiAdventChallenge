package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_classifications",
    indices = [
        Index(value = ["branchId"], name = "index_memory_classifications_branchId"),
        Index(value = ["createdAt"], name = "index_memory_classifications_createdAt")
    ]
)
data class MemoryClassificationEntity(
    @PrimaryKey
    val id: String,
    val userMessage: String,
    val branchId: String,
    val action: String,
    val memoryType: String?,
    val reason: String?,
    val importance: Float?,
    val createdAt: Long = System.currentTimeMillis(),
    val executionTimeMs: Long,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val isMultiple: Boolean = false,
    val requestId: String? = null
)