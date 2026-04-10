package com.example.mcp.server.dto.fitness_export

import kotlinx.serialization.Serializable

@Serializable
data class FitnessExportPipelineData(
    val success: Boolean,
    val filePath: String?,
    val format: String?,
    val savedAt: Long?,
    val search: FitnessLogsSearchData? = null,
    val summary: FitnessSummaryData? = null
)
