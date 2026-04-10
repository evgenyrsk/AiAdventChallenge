package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.dto.pipeline.CreateEventInput
import com.example.mcp.server.dto.pipeline.CreateEventOutput
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import com.example.mcp.server.service.reminder.ReminderService

class CreateEventStep(
    private val reminderService: ReminderService
) : AbstractPipelineStep<CreateEventInput, CreateEventOutput>(
    stepName = "create_event",
    description = "Create reminder event"
) {

    override suspend fun doExecute(
        input: CreateEventInput,
        context: PipelineContext
    ): PipelineResult<CreateEventOutput> {
        val event = reminderService.createReminderEvent(
            reminder = input.reminder,
            context = input.context,
            personalizedMessage = input.message
        )

        return if (event != null) {
            PipelineResult.Success(
                stepName = stepName,
                data = CreateEventOutput(
                    eventId = event.id,
                    status = event.status
                )
            )
        } else {
            PipelineResult.Failure(
                stepName = stepName,
                errorMessage = "Failed to create reminder event"
            )
        }
    }
}