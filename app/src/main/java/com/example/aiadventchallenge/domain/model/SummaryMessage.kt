package com.example.aiadventchallenge.domain.model

data class SummaryMessage(
    val id: String,
    val content: String,
    val messageRangeStart: Long,
    val messageRangeEnd: Long,
    val messageCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)
