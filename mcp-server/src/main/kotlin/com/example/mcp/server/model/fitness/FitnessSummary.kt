package com.example.mcp.server.model.fitness

import kotlinx.serialization.Serializable

@Serializable
data class FitnessSummaryResult(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val adherenceScore: Double,
    val summaryText: String
)

@Serializable
data class ScheduledSummaryResult(
    val id: String,
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val adherenceScore: Double,
    val summaryText: String,
    val createdAt: String
)

@Serializable
data class AddFitnessLogResult(
    val success: Boolean,
    val id: String,
    val message: String
)

@Serializable
data class RunScheduledSummaryResult(
    val success: Boolean,
    val summaryId: String?,
    val message: String,
    val summary: ScheduledSummaryResult?
)