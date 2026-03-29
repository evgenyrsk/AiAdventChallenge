package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "facts")
data class FactEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val source: String,
    val updatedAt: Long,
    val confidence: Float? = null,
    val isOptional: Boolean = false
)
