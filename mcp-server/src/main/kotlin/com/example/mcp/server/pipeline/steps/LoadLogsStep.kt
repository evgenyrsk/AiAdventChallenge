package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.dto.pipeline.LoadLogsInput
import com.example.mcp.server.dto.pipeline.LoadLogsStepOutput
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LoadLogsStep(
    private val repository: FitnessReminderRepository
) : AbstractPipelineStep<LoadLogsInput, LoadLogsStepOutput>(
    stepName = "load_logs",
    description = "Load fitness logs for a specified period"
) {

    override suspend fun doExecute(
        input: LoadLogsInput,
        context: PipelineContext
    ): PipelineResult<LoadLogsStepOutput> {
        val toDate = input.toDate ?: LocalDate.now()
        val startDate = toDate.minusDays(input.days.toLong() - 1)

        val logs = repository.getFitnessLogsByDateRange(
            startDate.format(DateTimeFormatter.ISO_DATE),
            toDate.format(DateTimeFormatter.ISO_DATE)
        )

        val period = when (input.days) {
            7 -> "last_7_days"
            30 -> "last_30_days"
            else -> "custom"
        }

        return PipelineResult.Success(
            stepName = stepName,
            data = LoadLogsStepOutput(
                logs = logs,
                period = period,
                startDate = startDate.format(DateTimeFormatter.ISO_DATE),
                endDate = toDate.format(DateTimeFormatter.ISO_DATE)
            )
        )
    }
}