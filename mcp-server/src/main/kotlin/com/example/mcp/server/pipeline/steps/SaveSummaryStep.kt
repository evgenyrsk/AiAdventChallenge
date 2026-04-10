package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.dto.pipeline.CalculateSummaryOutput
import com.example.mcp.server.dto.pipeline.SaveSummaryOutput
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult

class SaveSummaryStep(
    private val repository: FitnessReminderRepository
) : AbstractPipelineStep<CalculateSummaryOutput, SaveSummaryOutput>(
    stepName = "save_summary",
    description = "Save fitness summary to database"
) {

    override suspend fun doExecute(
        input: CalculateSummaryOutput,
        context: PipelineContext
    ): PipelineResult<SaveSummaryOutput> {
        val scheduledSummary = ScheduledSummary(
            period = input.summary.period,
            entriesCount = input.summary.entriesCount,
            avgWeight = input.summary.avgWeight,
            workoutsCompleted = input.summary.workoutsCompleted,
            avgSteps = input.summary.avgSteps,
            avgSleepHours = input.summary.avgSleepHours,
            avgProtein = input.summary.avgProtein,
            adherenceScore = input.summary.adherenceScore,
            summaryText = input.summary.summaryText,
            createdAt = System.currentTimeMillis()
        )

        val saved = repository.addScheduledSummary(scheduledSummary)

        if (saved) {
            return PipelineResult.Success(
                stepName = stepName,
                data = SaveSummaryOutput(
                    saved = true,
                    summaryId = ScheduledSummary.generateId(),
                    savedAt = System.currentTimeMillis()
                )
            )
        } else {
            return PipelineResult.Failure(
                stepName = stepName,
                errorMessage = "Failed to save summary to database"
            )
        }
    }
}