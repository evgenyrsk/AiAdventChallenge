package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.dto.fitness_export.FitnessLogEntry
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class SearchFitnessLogsStepInput(
    val period: String,
    val days: Int = 7
)

@Serializable
data class SearchFitnessLogsStepOutput(
    val period: String,
    val entries: List<FitnessLogEntry>,
    val startDate: String,
    val endDate: String
)

@Serializable
data class SummarizeFitnessLogsStepInput(
    val period: String,
    val entries: List<FitnessLogEntry>
)

@Serializable
data class SummarizeFitnessLogsStepOutput(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val summaryText: String
)

@Serializable
data class SaveSummaryToFileStepInput(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val summaryText: String,
    val format: String = "json"
)

@Serializable
data class SaveSummaryToFileStepOutput(
    val filePath: String,
    val format: String,
    val savedAt: Long
)