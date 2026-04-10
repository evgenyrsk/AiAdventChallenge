package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.dto.pipeline.CalculateSummaryOutput
import com.example.mcp.server.dto.pipeline.LoadLogsStepOutput
import com.example.mcp.server.model.fitness.FitnessLogEntity
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import com.example.mcp.server.service.fitness.FitnessSummaryService

class CalculateSummaryStep(
    private val summaryService: FitnessSummaryService
) : AbstractPipelineStep<LoadLogsStepOutput, CalculateSummaryOutput>(
    stepName = "calculate_summary",
    description = "Calculate fitness summary from logs"
) {

    override suspend fun doExecute(
        input: LoadLogsStepOutput,
        context: PipelineContext
    ): PipelineResult<CalculateSummaryOutput> {
        val summary = summaryService.generateSummary(input.logs, input.period)

        val metrics = buildMap<String, Double> {
            put("avg_weight", input.logs.mapNotNull { it.weight }.average().takeIf { it.isFinite() } ?: 0.0)
            put("avg_steps", input.logs.mapNotNull { it.steps }.average().toInt().toDouble())
            put("avg_sleep", input.logs.mapNotNull { it.sleepHours }.average().takeIf { it.isFinite() } ?: 0.0)
            put("avg_protein", input.logs.mapNotNull { it.protein }.average().toInt().toDouble())
            put("workout_rate", input.logs.count { it.workoutCompleted }.toDouble() / input.logs.size.toDouble())
            put("entries_count", input.logs.size.toDouble())
        }

        return PipelineResult.Success(
            stepName = stepName,
            data = CalculateSummaryOutput(
                summary = summary,
                metrics = metrics
            )
        )
    }
}