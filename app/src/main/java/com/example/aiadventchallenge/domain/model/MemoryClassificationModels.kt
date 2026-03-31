package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.memory.ClassificationResult
import kotlinx.serialization.Serializable

@Serializable
data class MemoryClassificationMetrics(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val executionTimeMs: Long
)

@Serializable
data class MemoryClassificationRequest(
    val userMessage: String,
    val existingWorkingMemory: List<MemoryEntryCompact>,
    val existingLongTermMemory: List<MemoryEntryCompact>
)

@Serializable
data class MemoryEntryCompact(
    val key: String,
    val value: String,
    val reason: String
)

@Serializable
data class MemoryClassificationResponse(
    val classifications: List<SingleClassificationItem>?
)

@Serializable
data class SingleClassificationItem(
    val action: String,
    val memoryType: String?,
    val reason: String?,
    val importance: Float?,
    val value: String?,
    val key: String?
)

data class MultipleClassificationResult(
    val results: List<ClassificationResult>,
    val metrics: MemoryClassificationMetrics
)