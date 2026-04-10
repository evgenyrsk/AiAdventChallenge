package com.example.mcp.server.dto.fitness_export

import kotlinx.serialization.Serializable

@Serializable
data class FitnessSummaryExportData(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val summaryText: String,
    val exportedAt: String
)
