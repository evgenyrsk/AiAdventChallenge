package com.example.mcp.server.dto.fitness_export

import kotlinx.serialization.Serializable

@Serializable
data class SaveSummaryToFileInput(
    val period: String,
    val entriesCount: Int,
    val avgWeight: Double?,
    val workoutsCompleted: Int,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val avgProtein: Int?,
    val summaryText: String,
    val format: String = "json"  // "json" or "txt"
)

@Serializable
data class SaveSummaryToFileOutput(
    val success: Boolean = true,
    val tool: String = "save_summary_to_file",
    val data: FileSaveData? = null,
    val error: String? = null
)

@Serializable
data class FileSaveData(
    val filePath: String,
    val format: String,
    val savedAt: String
)