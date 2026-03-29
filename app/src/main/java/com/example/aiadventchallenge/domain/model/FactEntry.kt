package com.example.aiadventchallenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FactEntry(
    val key: String,
    val value: String,
    val source: FactSource = FactSource.EXTRACTED,
    val updatedAt: Long = System.currentTimeMillis(),
    val confidence: Float? = null,
    val isOptional: Boolean = false
) {
    enum class FactSource {
        EXTRACTED,
        MANUAL,
        SYSTEM
    }
}
