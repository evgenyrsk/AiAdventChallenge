package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.dto.fitness_export.FitnessLogEntry
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class SearchFitnessLogsStep(
    private val repository: FitnessReminderRepository
) : AbstractPipelineStep<SearchFitnessLogsStepInput, SearchFitnessLogsStepOutput>(
    stepName = "search_fitness_logs",
    description = "Search fitness logs for a specified period"
) {

    override suspend fun doExecute(
        input: SearchFitnessLogsStepInput,
        context: PipelineContext
    ): PipelineResult<SearchFitnessLogsStepOutput> {
        val toDate = LocalDate.now()
        val startDate = toDate.minusDays(input.days.toLong() - 1)

        val logs = repository.getFitnessLogsByDateRange(
            startDate.format(DateTimeFormatter.ISO_DATE),
            toDate.format(DateTimeFormatter.ISO_DATE)
        )

        val entries = logs.map { entity ->
            FitnessLogEntry(
                date = entity.date,
                weight = entity.weight,
                calories = entity.calories,
                protein = entity.protein,
                workoutCompleted = entity.workoutCompleted,
                steps = entity.steps,
                sleepHours = entity.sleepHours,
                notes = entity.notes
            )
        }

        return PipelineResult.Success(
            stepName = stepName,
            data = SearchFitnessLogsStepOutput(
                period = input.period,
                entries = entries,
                startDate = startDate.format(DateTimeFormatter.ISO_DATE),
                endDate = toDate.format(DateTimeFormatter.ISO_DATE)
            )
        )
    }
}