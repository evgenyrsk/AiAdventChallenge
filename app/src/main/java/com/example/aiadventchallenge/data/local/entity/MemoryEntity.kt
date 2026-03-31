package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "memory_entries",
    indices = [
        Index(value = ["memoryType"], name = "index_memory_entries_type"),
        Index(value = ["branchId"], name = "index_memory_entries_branchId"),
        Index(value = ["isActive"], name = "index_memory_entries_active"),
        Index(value = ["key"], name = "index_memory_entries_key")
    ]
)
data class MemoryEntity(
    @androidx.room.PrimaryKey
    val id: String,
    val key: String,
    val value: String,
    val memoryType: String,    // храним как String enum
    val reason: String,        // храним как String enum
    val source: String,        // храним как String enum
    val importance: Float,
    val branchId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = true,
    val ttl: Long? = null
)