package com.example.mcp.server.pipeline.steps

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.dto.pipeline.CheckRemindersInput
import com.example.mcp.server.dto.pipeline.CheckRemindersOutput
import com.example.mcp.server.pipeline.AbstractPipelineStep
import com.example.mcp.server.pipeline.PipelineContext
import com.example.mcp.server.pipeline.PipelineResult
import com.example.mcp.server.service.reminder.ReminderService
import java.time.format.DateTimeFormatter

class CheckRemindersStep(
    private val reminderService: ReminderService
) : AbstractPipelineStep<CheckRemindersInput, CheckRemindersOutput>(
    stepName = "check_reminders",
    description = "Check for due reminders and build context"
) {

    override suspend fun doExecute(
        input: CheckRemindersInput,
        context: PipelineContext
    ): PipelineResult<CheckRemindersOutput> {
        val dueReminders = reminderService.getDueReminders(input.date, input.currentTime)
        val reminderContext = reminderService.buildReminderContext(input.date)

        return PipelineResult.Success(
            stepName = stepName,
            data = CheckRemindersOutput(
                dueReminders = dueReminders,
                context = reminderContext
            )
        )
    }
}