package com.example.aiadventchallenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatBranch(
    val id: String,
    val parentBranchId: String? = null,
    val checkpointMessageId: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)
