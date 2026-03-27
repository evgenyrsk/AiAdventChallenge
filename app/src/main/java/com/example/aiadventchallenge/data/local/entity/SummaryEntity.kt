package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val messageRangeStart: Long,
    val messageRangeEnd: Long,
    val messageCount: Int,
    val createdAt: Long
)
