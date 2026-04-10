package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.dto.pipeline.AnalyzeReminderInput
import com.example.mcp.server.dto.pipeline.AnalyzeReminderOutput
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import com.example.mcp.server.service.reminder.ReminderAnalysisService

class AnalyzeReminderStep(
    private val analysisService: ReminderAnalysisService
) : AbstractPipelineStep<AnalyzeReminderInput, AnalyzeReminderOutput>(
    stepName = "analyze_reminder",
    description = "Analyze reminder and determine if it should be triggered"
) {

    override suspend fun doExecute(
        input: AnalyzeReminderInput,
        context: PipelineContext
    ): PipelineResult<AnalyzeReminderOutput> {
        val decision = analysisService.shouldTriggerReminder(input.reminder, input.context)
        val personalizedMessage = analysisService.personalizeReminderMessage(input.reminder, input.context)

        return PipelineResult.Success(
            stepName = stepName,
            data = AnalyzeReminderOutput(
                shouldTrigger = decision.shouldTrigger,
                personalizedMessage = personalizedMessage,
                priority = decision.priority
            )
        )
    }
}