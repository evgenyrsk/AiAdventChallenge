package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey 
    val taskId: String,
    val query: String,
    val phase: String,
    val currentStep: Int,
    val totalSteps: Int,
    val currentAction: String,
    val plan: String,
    val done: String,
    val profile: String,
    val isActive: Boolean,
    val awaitingConfirmation: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
