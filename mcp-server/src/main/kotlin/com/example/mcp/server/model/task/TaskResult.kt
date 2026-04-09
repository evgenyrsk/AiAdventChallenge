package com.example.mcp.server.model.task

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleTaskResult(
    val success: Boolean,
    val taskId: String?,
    val message: String,
    val scheduledAt: String
)

@Serializable
data class PendingRemindersResult(
    val tasks: List<TaskSummary>
)

@Serializable
data class TaskSummary(
    val id: String,
    val type: String,
    val message: String?,
    val scheduledAt: String,
    val status: String
)

@Serializable
data class CancelTaskResult(
    val success: Boolean,
    val taskId: String,
    val message: String
)

@Serializable
data class RunTaskResult(
    val success: Boolean,
    val taskId: String?,
    val message: String,
    val output: String?
)
