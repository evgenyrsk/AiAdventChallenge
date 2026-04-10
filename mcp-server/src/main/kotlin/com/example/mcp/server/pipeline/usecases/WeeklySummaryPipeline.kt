package com.example.mcp.server.pipeline.usecases

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.dto.pipeline.*
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.pipeline.*
import com.example.mcp.server.pipeline.steps.*
import com.example.mcp.server.service.fitness.FitnessSummaryService
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

class WeeklySummaryPipeline(
    private val repository: FitnessReminderRepository,
    private val summaryService: FitnessSummaryService,
    private val executor: PipelineExecutor = PipelineExecutor.create()
) {

    suspend fun executeWeeklySummary(
        days: Int = 7,
        toDate: LocalDate = LocalDate.now()
    ): PipelineResult<WeeklySummaryResult> {
        val pipelineId = "weekly_summary_${UUID.randomUUID()}"
        val context = PipelineContext.create(pipelineId, "Weekly Summary Pipeline")

        val steps = listOf(
            LoadLogsStep(repository) as PipelineStep<*, *>,
            CalculateSummaryStep(summaryService) as PipelineStep<*, *>,
            SaveSummaryStep(repository) as PipelineStep<*, *>
        )

        val initialInput = LoadLogsInput(
            days = days,
            toDate = toDate
        )

        val result: PipelineResult<*> = executor.execute(
            steps,
            initialInput,
            context
        )

        return when (result) {
            is PipelineResult.Success -> {
                @Suppress("UNCHECKED_CAST")
                val saveResult = result.data as SaveSummaryOutput
                PipelineResult.Success(
                    stepName = "weekly_summary_complete",
                    data = WeeklySummaryResult(
                        success = true,
                        summaryId = saveResult.summaryId ?: "",
                        savedAt = saveResult.savedAt,
                        errorMessage = null
                    )
                )
            }
            is PipelineResult.Failure -> {
                PipelineResult.Success(
                    stepName = "weekly_summary_complete",
                    data = WeeklySummaryResult(
                        success = false,
                        summaryId = null,
                        savedAt = 0L,
                        errorMessage = result.errorMessage
                    )
                )
            }
        }
    }

    suspend fun executeWeeklySummaryWithOutput(
        days: Int = 7,
        toDate: LocalDate = LocalDate.now()
    ): Pair<PipelineResult<WeeklySummaryResult>, ScheduledSummary?> {
        val pipelineId = "weekly_summary_${UUID.randomUUID()}"
        val context = PipelineContext.create(pipelineId, "Weekly Summary Pipeline")

        val steps = listOf(
            LoadLogsStep(repository) as PipelineStep<*, *>,
            CalculateSummaryStep(summaryService) as PipelineStep<*, *>,
            SaveSummaryStep(repository) as PipelineStep<*, *>
        )

        val initialInput = LoadLogsInput(
            days = days,
            toDate = toDate
        )

        val result: PipelineResult<*> = executor.execute(
            steps,
            initialInput,
            context
        )

        if (result is PipelineResult.Success) {
            val logsOutput = context.getData<LoadLogsStepOutput>("load_logs")
            val summaryOutput = context.getData<CalculateSummaryOutput>("calculate_summary")

            if (logsOutput != null && summaryOutput != null) {
                val scheduledSummary = ScheduledSummary(
                    period = logsOutput.period,
                    entriesCount = summaryOutput.summary.entriesCount,
                    avgWeight = summaryOutput.summary.avgWeight,
                    workoutsCompleted = summaryOutput.summary.workoutsCompleted,
                    avgSteps = summaryOutput.summary.avgSteps,
                    avgSleepHours = summaryOutput.summary.avgSleepHours,
                    avgProtein = summaryOutput.summary.avgProtein,
                    adherenceScore = summaryOutput.summary.adherenceScore,
                    summaryText = summaryOutput.summary.summaryText,
                    createdAt = System.currentTimeMillis()
                )

                val successResult = PipelineResult.Success(
                    stepName = "weekly_summary_complete",
                    data = WeeklySummaryResult(
                        success = true,
                        summaryId = scheduledSummary.toEntity().id,
                        savedAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                )
                return successResult to scheduledSummary
            }
        }

        if (result is PipelineResult.Failure) {
            return PipelineResult.Success(
                stepName = "weekly_summary_complete",
                data = WeeklySummaryResult(
                    success = false,
                    summaryId = null,
                    savedAt = 0L,
                    errorMessage = result.errorMessage
                )
            ) to null
        }

        return (result as PipelineResult.Success<*>).let { successResult ->
            PipelineResult.Success(
                stepName = "weekly_summary_complete",
                data = WeeklySummaryResult(
                    success = true,
                    summaryId = null,
                    savedAt = System.currentTimeMillis(),
                    errorMessage = null
                )
            ) to null
        }
    }
}

@Serializable
data class WeeklySummaryResult(
    val success: Boolean,
    val summaryId: String?,
    val savedAt: Long,
    val errorMessage: String?
)