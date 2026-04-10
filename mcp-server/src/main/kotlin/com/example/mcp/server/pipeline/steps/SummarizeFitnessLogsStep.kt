package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.dto.fitness_export.FitnessLogEntry
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult

class SummarizeFitnessLogsStep : AbstractPipelineStep<SearchFitnessLogsStepOutput, SummarizeFitnessLogsStepOutput>(
    stepName = "summarize_fitness_logs",
    description = "Aggregate fitness logs and generate summary"
) {

    override suspend fun doExecute(
        input: SearchFitnessLogsStepOutput,
        context: PipelineContext
    ): PipelineResult<SummarizeFitnessLogsStepOutput> {
        if (input.entries.isEmpty()) {
            return PipelineResult.Success(
                stepName = stepName,
                data = SummarizeFitnessLogsStepOutput(
                    period = input.period,
                    entriesCount = 0,
                    avgWeight = null,
                    workoutsCompleted = 0,
                    avgSteps = null,
                    avgSleepHours = null,
                    avgProtein = null,
                    summaryText = "Нет данных за выбранный период."
                )
            )
        }

        val entriesCount = input.entries.size

        val weights = input.entries.mapNotNull { it.weight }
        val avgWeight = weights.average().takeIf { weights.isNotEmpty() }

        val workoutsCompleted = input.entries.count { it.workoutCompleted }

        val steps = input.entries.mapNotNull { it.steps }
        val avgSteps = steps.average().toInt().takeIf { steps.isNotEmpty() }

        val sleepHours = input.entries.mapNotNull { it.sleepHours }
        val avgSleepHours = sleepHours.average().takeIf { sleepHours.isNotEmpty() }

        val protein = input.entries.mapNotNull { it.protein }
        val avgProtein = protein.average().toInt().takeIf { protein.isNotEmpty() }

        val summaryText = generateSummaryText(
            workoutsCompleted,
            entriesCount,
            avgProtein,
            avgSleepHours,
            avgSteps,
            avgWeight
        )

        return PipelineResult.Success(
            stepName = stepName,
            data = SummarizeFitnessLogsStepOutput(
                period = input.period,
                entriesCount = entriesCount,
                avgWeight = avgWeight,
                workoutsCompleted = workoutsCompleted,
                avgSteps = avgSteps,
                avgSleepHours = avgSleepHours,
                avgProtein = avgProtein,
                summaryText = summaryText
            )
        )
    }

    private fun generateSummaryText(
        workoutsCompleted: Int,
        entriesCount: Int,
        avgProtein: Int?,
        avgSleepHours: Double?,
        avgSteps: Int?,
        avgWeight: Double?
    ): String {
        val observations = mutableListOf<String>()

        val workoutRate = if (entriesCount > 0) workoutsCompleted.toDouble() / entriesCount else 0.0

        if (workoutRate < 0.5 && entriesCount >= 3) {
            observations.add("Низкая регулярность тренировок ($workoutsCompleted из $entriesCount дней)")
        } else if (workoutRate >= 0.7) {
            observations.add("Хорошая регулярность тренировок")
        }

        if (avgProtein != null && avgProtein < 120) {
            observations.add("Недостаток белка в рационе")
        } else if (avgProtein != null && avgProtein >= 150) {
            observations.add("Хороший уровень потребления белка")
        }

        if (avgSleepHours != null && avgSleepHours < 7.0) {
            observations.add("Недостаточное время сна для восстановления")
        } else if (avgSleepHours != null && avgSleepHours >= 7.5) {
            observations.add("Достаточное время сна")
        }

        if (avgSteps != null && avgSteps < 7000) {
            observations.add("Низкая бытовая активность")
        } else if (avgSteps != null && avgSteps >= 10000) {
            observations.add("Хороший уровень бытовой активности")
        }

        val baseSummary = buildString {
            append("За период выполнено $workoutsCompleted тренировок")
            if (avgWeight != null) {
                append(", средний вес ${String.format("%.1f", avgWeight)} кг")
            }
            if (avgSleepHours != null) {
                append(", средний сон ${String.format("%.1f", avgSleepHours)} ч")
            }
            append(". ")
        }

        return if (observations.isNotEmpty()) {
            baseSummary + observations.joinToString(", ") + "."
        } else {
            baseSummary + "Активность стабильная."
        }
    }
}