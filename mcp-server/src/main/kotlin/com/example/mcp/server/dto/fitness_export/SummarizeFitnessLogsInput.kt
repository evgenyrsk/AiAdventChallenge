package com.example.mcp.server.dto.fitness_export

import kotlinx.serialization.Serializable

@Serializable
data class SummarizeFitnessLogsInput(
    val period: String,
    val entries: List<FitnessLogEntry>
)

@Serializable
data class SummarizeFitnessLogsOutput(
    val success: Boolean = true,
    val tool: String = "summarize_fitness_logs",
    val data: FitnessSummaryData? = null,
    val error: String? = null
)

@Serializable
data class FitnessSummaryData(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val summaryText: String
)