package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey
    val id: String,
    val parentBranchId: String? = null,
    val checkpointMessageId: String,
    val lastMessageId: String?,
    val title: String,
    val createdAt: Long,
    val isActive: Boolean = false
)
