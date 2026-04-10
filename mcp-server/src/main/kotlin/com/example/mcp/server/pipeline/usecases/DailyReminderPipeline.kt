package com.example.mcp.server.pipeline.usecases

import com.example.mcp.server.dto.pipeline.*
import com.example.mcp.server.model.reminder.EventStatus
import com.example.mcp.server.model.reminder.ReminderEvent
import com.example.mcp.server.pipeline.*
import com.example.mcp.server.pipeline.steps.*
import com.example.mcp.server.service.reminder.ReminderAnalysisService
import com.example.mcp.server.service.reminder.ReminderService
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class DailyReminderPipeline(
    private val reminderService: ReminderService,
    private val analysisService: ReminderAnalysisService,
    private val executor: PipelineExecutor = PipelineExecutor.create()
) {

    suspend fun executeDailyReminders(
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now()
    ): PipelineResult<DailyReminderResult> {
        val pipelineId = "daily_reminder_${UUID.randomUUID()}"
        val context = PipelineContext.create(pipelineId, "Daily Reminder Pipeline")

        val checkResult = executeCheckReminders(date, time, context)
        
        if (checkResult is PipelineResult.Failure) {
            return PipelineResult.Success(
                stepName = "daily_reminder_complete",
                data = DailyReminderResult(
                    success = false,
                    createdEvents = emptyList(),
                    skippedEvents = 0,
                    errorMessage = checkResult.errorMessage
                )
            )
        }

        @Suppress("UNCHECKED_CAST")
        val checkOutput = (checkResult as PipelineResult.Success<CheckRemindersOutput>).data
        
        val createdEvents = mutableListOf<ReminderEvent>()
        var skippedCount = 0

        for (reminder in checkOutput.dueReminders) {
            val analyzeResult = executeAnalyzeReminder(reminder, checkOutput.context, context)
            
            if (analyzeResult is PipelineResult.Failure) {
                skippedCount++
                continue
            }

            @Suppress("UNCHECKED_CAST")
            val analyzeOutput = (analyzeResult as PipelineResult.Success<AnalyzeReminderOutput>).data

            if (analyzeOutput.shouldTrigger) {
                val createResult = executeCreateEvent(reminder, checkOutput.context, analyzeOutput.personalizedMessage, context)
                
                if (createResult is PipelineResult.Success) {
                    @Suppress("UNCHECKED_CAST")
                    val createOutput = createResult.data as CreateEventOutput
                    val event = ReminderEvent(
                        id = createOutput.eventId ?: "",
                        reminderId = reminder.id,
                        type = reminder.type,
                        scheduledTime = System.currentTimeMillis(),
                        triggeredAt = null,
                        status = createOutput.status,
                        context = checkOutput.context,
                        response = null
                    )
                    createdEvents.add(event)
                } else {
                    skippedCount++
                }
            } else {
                skippedCount++
            }
        }

        return PipelineResult.Success(
            stepName = "daily_reminder_complete",
            data = DailyReminderResult(
                success = true,
                createdEvents = createdEvents,
                skippedEvents = skippedCount,
                errorMessage = null
            )
        )
    }

    private suspend fun executeCheckReminders(
        date: LocalDate,
        time: LocalTime,
        context: PipelineContext
    ): PipelineResult<CheckRemindersOutput> {
        val step = CheckRemindersStep(reminderService)
        val input = CheckRemindersInput(date = date, currentTime = time)
        
        val result = executor.executeStep(step, input, context)
        
        return if (result is PipelineResult.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            PipelineResult.Success(
                stepName = step.stepName,
                data = result.data as CheckRemindersOutput
            )
        } else {
            @Suppress("UNCHECKED_CAST")
            result as PipelineResult.Failure
        }
    }

    private suspend fun executeAnalyzeReminder(
        reminder: com.example.mcp.server.model.reminder.Reminder,
        context: com.example.mcp.server.model.reminder.ReminderContext,
        pipelineContext: PipelineContext
    ): PipelineResult<AnalyzeReminderOutput> {
        val step = AnalyzeReminderStep(analysisService)
        val input = AnalyzeReminderInput(reminder = reminder, context = context)
        
        val result = executor.executeStep(step, input, pipelineContext)
        
        return if (result is PipelineResult.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            PipelineResult.Success(
                stepName = step.stepName,
                data = result.data as AnalyzeReminderOutput
            )
        } else {
            @Suppress("UNCHECKED_CAST")
            result as PipelineResult.Failure
        }
    }

    private suspend fun executeCreateEvent(
        reminder: com.example.mcp.server.model.reminder.Reminder,
        context: com.example.mcp.server.model.reminder.ReminderContext,
        message: String,
        pipelineContext: PipelineContext
    ): PipelineResult<CreateEventOutput> {
        val step = CreateEventStep(reminderService)
        val input = CreateEventInput(reminder = reminder, context = context, message = message)

        val result = executor.executeStep(step, input, pipelineContext)

        return if (result is PipelineResult.Success<*>) {
            @Suppress("UNCHECKED_CAST")
            (result as PipelineResult.Success<*>).data?.let { data ->
                if (data is CreateEventOutput) {
                    PipelineResult.Success(
                        stepName = step.stepName,
                        data = data
                    )
                } else {
                    PipelineResult.Failure(
                        stepName = step.stepName,
                        errorMessage = "Unexpected result type"
                    )
                }
            } ?: PipelineResult.Failure(
                stepName = step.stepName,
                errorMessage = "Result data is null"
            )
        } else {
            @Suppress("UNCHECKED_CAST")
            result as PipelineResult.Failure
        }
    }

    suspend fun executeSingleReminder(
        reminder: com.example.mcp.server.model.reminder.Reminder,
        date: LocalDate = LocalDate.now()
    ): PipelineResult<CreateEventOutput> {
        val pipelineId = "single_reminder_${UUID.randomUUID()}"
        val context = PipelineContext.create(pipelineId, "Single Reminder Pipeline")

        val reminderContext = reminderService.buildReminderContext(date)
        val analyzeInput = AnalyzeReminderInput(reminder, reminderContext)
        val analyzeStep = AnalyzeReminderStep(analysisService)
        
        val analyzeResult = executor.executeStep(analyzeStep, analyzeInput, context)
        
        if (analyzeResult is PipelineResult.Failure) {
            return analyzeResult
        }

        @Suppress("UNCHECKED_CAST")
        val analyzeOutput = (analyzeResult as PipelineResult.Success<*>).data as AnalyzeReminderOutput

        if (analyzeOutput.shouldTrigger) {
            val createInput = CreateEventInput(reminder, reminderContext, analyzeOutput.personalizedMessage)
            val createStep = CreateEventStep(reminderService)

            val result = executor.executeStep(createStep, createInput, context)
            return if (result is PipelineResult.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                result.data?.let { data ->
                    if (data is CreateEventOutput) {
                        PipelineResult.Success(
                            stepName = createStep.stepName,
                            data = data
                        )
                    } else {
                        PipelineResult.Failure(
                            stepName = createStep.stepName,
                            errorMessage = "Unexpected result type"
                        )
                    }
                } ?: PipelineResult.Failure(
                    stepName = createStep.stepName,
                    errorMessage = "Result data is null"
                )
            } else {
                @Suppress("UNCHECKED_CAST")
                result as PipelineResult.Failure
            }
        } else {
            return PipelineResult.Failure(
                stepName = "single_reminder",
                errorMessage = "Reminder should not be triggered based on analysis"
            )
        }
    }
}

data class DailyReminderResult(
    val success: Boolean,
    val createdEvents: List<ReminderEvent>,
    val skippedEvents: Int,
    val errorMessage: String?
)