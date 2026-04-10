package com.example.mcp.server.dto.fitness_export

import kotlinx.serialization.Serializable

@Serializable
data class FitnessSummaryExportResult(
    val success: Boolean,
    val filePath: String?,
    val format: String?,
    val savedAt: Long?,
    val errorMessage: String?
)