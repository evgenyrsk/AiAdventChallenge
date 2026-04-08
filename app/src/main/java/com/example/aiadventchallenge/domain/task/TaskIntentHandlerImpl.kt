package com.example.aiadventchallenge.domain.task

import android.util.Log
import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.data.config.TaskIntent
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskAction
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.model.TaskStateMachine
import com.example.aiadventchallenge.domain.model.TransitionResult
import com.example.aiadventchallenge.domain.parser.UserResponseParser
import com.example.aiadventchallenge.domain.repository.TaskRepository

class TaskIntentHandlerImpl(
    private val taskRepository: TaskRepository,
    private val userResponseParser: UserResponseParser
) : TaskIntentHandler {
    
    private val TAG = "TaskIntentHandler"
    
    override suspend fun handleIntent(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext?
    ): TaskIntentResult {
        logIntentInfo(aiResponse, userInput, currentTask)
        
        return when (aiResponse.taskIntent) {
            TaskIntent.NEW_TASK -> handleNewTaskIntent(aiResponse, userInput, currentTask)
            TaskIntent.SWITCH_TASK -> handleSwitchTaskIntent(aiResponse, userInput, currentTask)
            TaskIntent.PAUSE_TASK -> handlePauseTaskIntent(currentTask)
            TaskIntent.CONTINUE_TASK,
            TaskIntent.CLARIFICATION -> handleContinueOrClarificationIntent(aiResponse, userInput, currentTask)
        }
    }
    
    private fun logIntentInfo(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext?
    ) {
        Log.d(TAG, "=== Task Intent Handler ===")
        Log.d(TAG, "Intent: ${aiResponse.taskIntent}")
        Log.d(TAG, "stepCompleted: ${aiResponse.stepCompleted}")
        Log.d(TAG, "taskCompleted: ${aiResponse.taskCompleted}")
        Log.d(TAG, "transitionTo: ${aiResponse.transitionTo}")
        Log.d(TAG, "nextAction: ${aiResponse.nextAction}")
        
        if (currentTask != null) {
            Log.d(TAG, "Current task:")
            Log.d(TAG, "  Phase: ${currentTask.phase.label} (${currentTask.currentStep}/${currentTask.totalSteps})")
            Log.d(TAG, "  Query: ${currentTask.query}")
            Log.d(TAG, "  isActive: ${currentTask.isActive}")
            Log.d(TAG, "  awaitingConfirmation: ${currentTask.awaitingUserConfirmation}")
        } else {
            Log.d(TAG, "No active task")
        }
        Log.d(TAG, "Has active task for intent processing: ${currentTask != null}")
    }
    
    private suspend fun handleNewTaskIntent(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext?
    ): TaskIntentResult {
        Log.d(TAG, "Action: Creating new task")
        val taskQuery = aiResponse.newTaskQuery ?: userInput
        
        if (currentTask != null) {
            Log.w(TAG, "LLM returned NEW_TASK but task already exists (ID: ${currentTask.taskId})")
            Log.w(TAG, "  Expected: CONTINUE_TASK, Actual: NEW_TASK")
            Log.w(TAG, "  Current task query: ${currentTask.query}")
            Log.w(TAG, "  New task query: $taskQuery")
            Log.w(TAG, "  Skipping task creation to avoid duplicate")
            return TaskIntentResult.NoAction
        }
        
        return createTask(taskQuery)
    }
    
    private suspend fun handleSwitchTaskIntent(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext?
    ): TaskIntentResult {
        Log.d(TAG, "Action: Switching task")
        
        if (currentTask != null) {
            pauseTaskInternal(currentTask.taskId)
        }
        
        val taskQuery = aiResponse.newTaskQuery ?: userInput
        return createTask(taskQuery)
    }
    
    private suspend fun handlePauseTaskIntent(currentTask: TaskContext?): TaskIntentResult {
        Log.d(TAG, "Action: Pausing task")
        
        if (currentTask == null) {
            Log.w(TAG, "No active task to pause")
            return TaskIntentResult.NoAction
        }
        
        return pauseTaskInternal(currentTask.taskId)
    }
    
    private suspend fun handleContinueOrClarificationIntent(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext?
    ): TaskIntentResult {
        if (currentTask == null) {
            Log.d(TAG, "Has active task for intent processing: false")
            return TaskIntentResult.NoAction
        }
        
        Log.d(TAG, "Action: Processing task continuation/clarification")
        Log.d(TAG, "Has active task for intent processing: true")
        Log.d(TAG, "Response: stepCompleted=${aiResponse.stepCompleted}, transitionTo=${aiResponse.transitionTo}")
        
        if (currentTask.awaitingUserConfirmation) {
            return handleAwaitingConfirmation(aiResponse, userInput, currentTask)
        }
        
        if (!currentTask.awaitingUserConfirmation && aiResponse.stepCompleted && aiResponse.transitionTo == null) {
            Log.w(TAG, "⚠️ Potential issue: step_completed=true without awaitingConfirmation")
            Log.w(TAG, "   Phase: ${currentTask.phase.label}, transitionTo: null")
            Log.w(TAG, "   This should only happen in non-awaiting mode")
        }
        
        if (aiResponse.transitionTo != null) {
            return handleExplicitTransition(aiResponse, currentTask)
        }
        
        if (aiResponse.taskCompleted) {
            return handleTaskCompletion(aiResponse, currentTask)
        }
        
        if (aiResponse.stepCompleted) {
            return handleStepCompletion(aiResponse, currentTask)
        }
        
        Log.d(TAG, "No transition action taken")
        return TaskIntentResult.NoAction
    }
    
    private suspend fun handleAwaitingConfirmation(
        aiResponse: EnhancedTaskAiResponse,
        userInput: String,
        currentTask: TaskContext
    ): TaskIntentResult {
        Log.d(TAG, "User confirmed - processing confirmation")
        
        val isAffirmative = userResponseParser.isAffirmative(userInput)
        
        if (isAffirmative) {
            Log.d(TAG, "User confirmed - transitioning to next phase")
            val nextPhase = TaskStateMachine().getNextPhase(currentTask.phase)
            
            if (nextPhase != null) {
                return transitionTo(currentTask.taskId, nextPhase)
            } else if (currentTask.phase != TaskPhase.DONE) {
                Log.d(TAG, "No next phase, transitioning to DONE")
                return transitionTo(currentTask.taskId, TaskPhase.DONE)
            }
            
            return TaskIntentResult.NoAction
        } else {
            Log.d(TAG, "User rejected or unclear - staying on current phase")
            
            if (currentTask.phase == TaskPhase.VALIDATION) {
                Log.d(TAG, "VALIDATION: Returning to EXECUTION for corrections")
                return transitionTo(currentTask.taskId, TaskPhase.EXECUTION)
            } else {
                Log.d(TAG, "Other phases: resetting awaiting confirmation")
                return setAwaitingConfirmation(currentTask.taskId, false)
            }
        }
    }
    
    private suspend fun handleExplicitTransition(
        aiResponse: EnhancedTaskAiResponse,
        currentTask: TaskContext
    ): TaskIntentResult {
        Log.d(TAG, "Explicit transition requested to: ${aiResponse.transitionTo?.label}")
        
        val isAutoTransition = when {
            currentTask.phase == TaskPhase.EXECUTION && aiResponse.transitionTo == TaskPhase.VALIDATION -> true
            currentTask.phase == TaskPhase.PLANNING && aiResponse.transitionTo == TaskPhase.VALIDATION -> true
            else -> false
        }
        
        if (isAutoTransition) {
            Log.w(TAG, "❌ Auto-transition detected: ${currentTask.phase.label} → ${aiResponse.transitionTo?.label}")
            return TaskIntentResult.SystemMessage(
                buildAutoTransitionWarningMessage(currentTask.phase, aiResponse.transitionTo ?: TaskPhase.DONE)
            )
        }
        
        val stateMachine = TaskStateMachine()
        val validationResult = stateMachine.validateTransitionBefore(
            currentTask.phase,
            aiResponse.transitionTo ?: currentTask.phase
        )
        
        return when (validationResult) {
            is TransitionResult.Allowed -> {
                Log.d(TAG, "✅ Transition ALLOWED by validation")
                transitionTo(currentTask.taskId, aiResponse.transitionTo!!)
            }
            is TransitionResult.Denied -> {
                Log.w(TAG, "❌ Transition DENIED by validation: ${validationResult.reason}")
                TaskIntentResult.SystemMessage(
                    """
                    ⚠️ ${validationResult.reason}
                    
                    💡 ${getValidTransitionsHint(currentTask.phase)}
                    """.trimIndent()
                )
            }
        }
    }
    
    private suspend fun handleTaskCompletion(
        aiResponse: EnhancedTaskAiResponse,
        currentTask: TaskContext
    ): TaskIntentResult {
        Log.d(TAG, "Task completed: true")
        
        if (currentTask.phase == TaskPhase.VALIDATION) {
            Log.d(TAG, "✅ Task completion ALLOWED: from VALIDATION phase")
            return transitionTo(currentTask.taskId, TaskPhase.DONE)
        } else {
            Log.w(TAG, "❌ Task completion DENIED: current phase is ${currentTask.phase.label}")
            Log.w(TAG, "   Reason: Task completion is only allowed from VALIDATION phase")
            
            return TaskIntentResult.SystemMessage(
                """
                ⚠️ Нельзя завершить задачу из фазы "${currentTask.phase.label}"
                
                💡 Завершение задачи разрешено только после фазы проверки (VALIDATION)
                💡 ${getValidTransitionsHint(currentTask.phase)}
                """.trimIndent()
            )
        }
    }
    
    private suspend fun handleStepCompletion(
        aiResponse: EnhancedTaskAiResponse,
        currentTask: TaskContext
    ): TaskIntentResult {
        Log.d(TAG, "Step completed: true")
        
        if (currentTask.currentStep >= currentTask.totalSteps) {
            if (currentTask.phase == TaskPhase.EXECUTION) {
                Log.d(TAG, "EXECUTION: Last step completed, auto-transitioning to VALIDATION")
                return transitionTo(currentTask.taskId, TaskPhase.VALIDATION)
            } else {
                Log.d(TAG, "Last step completed, awaiting user confirmation")
                return setAwaitingConfirmation(currentTask.taskId, true)
            }
        } else {
            Log.d(TAG, "Advancing to next step: ${currentTask.currentStep + 1}/${currentTask.totalSteps}")
            return advanceTask(currentTask.taskId)
        }
    }
    
    private suspend fun createTask(query: String): TaskIntentResult {
        Log.d(TAG, "=== Creating Task ===")
        Log.d(TAG, "Query: $query")
        
        val task = taskRepository.createTask(query, FitnessProfileType.INTERMEDIATE)
        
        Log.d(TAG, "Task created:")
        Log.d(TAG, "  ID: ${task.taskId}")
        Log.d(TAG, "  Phase: ${task.phase.label} (${task.currentStep}/${task.totalSteps})")
        Log.d(TAG, "  isActive: ${task.isActive}")
        Log.d(TAG, "=== Task Created ===\n")
        
        return TaskIntentResult.TaskUpdated(task)
    }
    
    private suspend fun pauseTaskInternal(taskId: String): TaskIntentResult {
        Log.d(TAG, "=== Pausing Task ===")
        Log.d(TAG, "Task ID: $taskId")
        
        val updatedTask = taskRepository.updateTask(taskId, TaskAction.Pause(taskId))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: isActive=${updatedTask.isActive}")
            Log.d(TAG, "=== Task Paused ===\n")
            return TaskIntentResult.TaskUpdated(updatedTask)
        }
        
        return TaskIntentResult.NoAction
    }
    
    private suspend fun transitionTo(taskId: String, toPhase: TaskPhase): TaskIntentResult {
        Log.d(TAG, "=== Transitioning Task ===")
        Log.d(TAG, "Task ID: $taskId")
        Log.d(TAG, "To: ${toPhase.label}")
        
        val stateMachine = TaskStateMachine()
        val currentTask = taskRepository.getTaskById(taskId) ?: return TaskIntentResult.NoAction
        
        val canTransition = stateMachine.canTransition(currentTask.phase, toPhase)
        Log.d(TAG, "Can transition: $canTransition")
        
        if (canTransition) {
            val updatedTask = taskRepository.updateTask(taskId, TaskAction.Transition(toPhase))
            
            if (updatedTask != null) {
                Log.d(TAG, "After: ${updatedTask.phase.label} (step ${updatedTask.currentStep}/${updatedTask.totalSteps})")
                Log.d(TAG, "=== Task Transitioned ===\n")
                return TaskIntentResult.TaskUpdated(updatedTask)
            }
        } else {
            Log.w(TAG, "Transition not allowed! Valid transitions: ${stateMachine.getPossibleTransitions(currentTask.phase).map { it.label }}")
            Log.d(TAG, "=== Task Transition Skipped ===\n")
        }
        
        return TaskIntentResult.NoAction
    }
    
    private suspend fun advanceTask(taskId: String): TaskIntentResult {
        Log.d(TAG, "=== Advancing Task ===")
        Log.d(TAG, "Task ID: $taskId")
        
        val currentTask = taskRepository.getTaskById(taskId) ?: return TaskIntentResult.NoAction
        Log.d(TAG, "Before: ${currentTask.phase.label} (step ${currentTask.currentStep}/${currentTask.totalSteps})")
        
        if (currentTask.canAdvance) {
            val updatedTask = taskRepository.updateTask(taskId, TaskAction.AdvanceStep())
            
            if (updatedTask != null) {
                Log.d(TAG, "After: ${updatedTask.phase.label} (step ${updatedTask.currentStep}/${updatedTask.totalSteps})")
                Log.d(TAG, "=== Task Advanced ===\n")
                return TaskIntentResult.TaskUpdated(updatedTask)
            }
        } else {
            Log.d(TAG, "Task cannot advance: isCompleted=${currentTask.isCompleted}, currentStep=${currentTask.currentStep}, totalSteps=${currentTask.totalSteps}")
            Log.d(TAG, "=== Task Advance Skipped ===\n")
        }
        
        return TaskIntentResult.NoAction
    }
    
    private suspend fun setAwaitingConfirmation(taskId: String, awaiting: Boolean): TaskIntentResult {
        Log.d(TAG, "=== Setting AwaitingConfirmation ===")
        Log.d(TAG, "Task ID: $taskId")
        Log.d(TAG, "Awaiting: $awaiting")
        
        val updatedTask = taskRepository.updateTask(taskId, TaskAction.SetAwaitingConfirmation(awaiting))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: ${updatedTask.awaitingUserConfirmation}")
            Log.d(TAG, "=== AwaitingConfirmation Set ===\n")
            return TaskIntentResult.TaskUpdated(updatedTask)
        }
        
        return TaskIntentResult.NoAction
    }
    
    private fun buildAutoTransitionWarningMessage(currentPhase: TaskPhase, targetPhase: TaskPhase): String {
        return """
            ⚠️ Автоматический переход на ${targetPhase.label} запрещен!
            
            💡 Правильный протокол для завершения фазы ${currentPhase.label}:
            1. Представьте результат работы
            2. Задайте вопрос пользователю
            3. Установите step_completed: true
            4. Система автоматически перейдет на следующую фазу после подтверждения
            
            ❌ Что запрещено:
            • Использовать transition_to для автоматических переходов вперед
            • Использовать фразы "Переходим к проверке", "Приступаем к проверке"
            
            ✅ Правильный пример:
            Вот план тренировок на неделю:
            Пн: Грудь + Трицепс
            ...
            
            Всё устраивает?
            
            step_completed: true
            transition_to: null
        """.trimIndent()
    }
    
    private fun getValidTransitionsHint(phase: TaskPhase): String {
        val transitions = TaskStateMachine().getPossibleTransitions(phase)
        return if (transitions.isEmpty()) {
            "Нет допустимых переходов (финальная фаза)"
        } else {
            "Допустимые переходы: ${transitions.joinToString { it.label }}"
        }
    }
}
