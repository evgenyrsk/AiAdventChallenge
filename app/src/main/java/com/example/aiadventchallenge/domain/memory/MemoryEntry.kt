package com.example.aiadventchallenge.domain.memory

data class MemoryEntry(
    val id: String,
    val key: String,
    val value: String,
    val memoryType: MemoryType,
    val reason: MemoryReason,
    val source: MemorySource,
    val importance: Float,      // 0.0 - 1.0
    val branchId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isActive: Boolean = true,
    val ttl: Long? = null       // timestamp истечения для working memory
)