package com.example.aiadventchallenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MemoryConfig(
    val shortTermWindow: Int = 10,           // размер окна для short-term
    val workingMemoryTTL: Long = 30 * 60 * 1000,  // TTL в ms (30 минут по умолчанию)
    val longTermImportanceThreshold: Float = 0.7f // порог importance для long-term
)