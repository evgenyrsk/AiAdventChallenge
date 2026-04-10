package com.example.mcp.server.dto.fitness_export

import kotlinx.serialization.Serializable

@Serializable
data class SearchFitnessLogsInput(
    val period: String,  // "last_7_days", "last_30_days"
    val days: Int = 7
)

@Serializable
data class FitnessLogEntry(
    val date: String,
    val weight: Double?,
    val calories: Int?,
    val protein: Int?,
    val workoutCompleted: Boolean,
    val steps: Int?,
    val sleepHours: Double?,
    val notes: String?
)

@Serializable
data class SearchFitnessLogsOutput(
    val success: Boolean = true,
    val tool: String = "search_fitness_logs",
    val data: FitnessLogsSearchData? = null,
    val error: String? = null
)

@Serializable
data class FitnessLogsSearchData(
    val period: String,
    val entries: List<FitnessLogEntry>,
    val startDate: String,
    val endDate: String
)