package com.example.aiadventchallenge.data.local.entity

import com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics
import com.example.aiadventchallenge.domain.memory.ClassificationResult

fun MemoryClassificationEntity.toDomain(): MemoryClassificationMetrics {
    return MemoryClassificationMetrics(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        executionTimeMs = executionTimeMs
    )
}

fun ClassificationResult.toEntity(
    message: com.example.aiadventchallenge.domain.model.ChatMessage,
    metrics: MemoryClassificationMetrics,
    isMultiple: Boolean = false
): MemoryClassificationEntity {
    val action: String
    val memoryTypeStr: String?
    val reasonStr: String?
    val importanceVal: Float?

    when (this) {
        is ClassificationResult.Create -> {
            action = "create"
            memoryTypeStr = memoryType.name.lowercase()
            reasonStr = reason.name
            importanceVal = importance
        }
        is ClassificationResult.Skip -> {
            action = "skip"
            memoryTypeStr = null
            reasonStr = null
            importanceVal = null
        }
    }

    val requestId = if (isMultiple) "req_${System.currentTimeMillis()}_${(0..9999).random()}" else null

    return MemoryClassificationEntity(
        id = "class_${System.currentTimeMillis()}_${(0..9999).random()}",
        userMessage = message.content,
        branchId = message.branchId,
        action = action,
        memoryType = memoryTypeStr,
        reason = reasonStr,
        importance = importanceVal,
        createdAt = System.currentTimeMillis(),
        executionTimeMs = metrics.executionTimeMs,
        promptTokens = metrics.promptTokens,
        completionTokens = metrics.completionTokens,
        totalTokens = metrics.totalTokens,
        isMultiple = isMultiple,
        requestId = requestId
    )
}