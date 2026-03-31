package com.example.aiadventchallenge.domain.memory

sealed class ClassificationResult {
    data class Create(
        val memoryType: MemoryType,
        val reason: MemoryReason,
        val importance: Float,
        val value: String,
        val source: MemorySource,
        val key: String? = null
    ) : ClassificationResult()

    object Skip : ClassificationResult()
}
