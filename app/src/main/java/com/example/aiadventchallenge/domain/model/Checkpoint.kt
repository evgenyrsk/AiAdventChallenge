package com.example.aiadventchallenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Checkpoint(
    val id: String,
    val messageId: String,
    val branchId: String,
    val createdAt: Long = System.currentTimeMillis()
)
