package com.example.mcp.server.dto.fitness_export

import com.example.mcp.server.pipeline.usecases.FitnessSummaryExportFullData
import kotlinx.serialization.Serializable

@Serializable
data class FitnessSummaryExportFullResponse(
    val exportResult: FitnessSummaryExportResult,
    val summaryData: FitnessSummaryExportFullData
)