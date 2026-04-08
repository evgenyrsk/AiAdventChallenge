package com.example.aiadventchallenge.domain.task

import android.util.Log
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskAction
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.model.TaskStateMachine
import com.example.aiadventchallenge.domain.repository.TaskRepository

class TaskCoordinatorImpl(
    private val taskRepository: TaskRepository
) : TaskCoordinator {
    
    private val TAG = "TaskCoordinator"
    private val stateMachine = TaskStateMachine()
    
    override suspend fun createTask(query: String, profile: FitnessProfileType): TaskContext? {
        Log.d(TAG, "=== Creating Task ===")
        Log.d(TAG, "Query: $query")
        Log.d(TAG, "Profile: ${profile.label}")
        
        val task = taskRepository.createTask(query, profile)
        
        Log.d(TAG, "Task created:")
        Log.d(TAG, "  ID: ${task.taskId}")
        Log.d(TAG, "  Phase: ${task.phase.label} (${task.currentStep}/${task.totalSteps})")
        Log.d(TAG, "  isActive: ${task.isActive}")
        Log.d(TAG, "=== Task Created ===\n")
        
        return task
    }
    
    override suspend fun advanceTask(): TaskContext? {
        Log.d(TAG, "=== Advancing Task ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to advance")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "Before: ${currentTask.phase.label} (step ${currentTask.currentStep}/${currentTask.totalSteps})")
        
        if (!currentTask.canAdvance) {
            Log.w(TAG, "Task cannot advance: isCompleted=${currentTask.isCompleted}, currentStep=${currentTask.currentStep}, totalSteps=${currentTask.totalSteps}")
            Log.d(TAG, "=== Task Advance Skipped ===\n")
            return currentTask
        }
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.AdvanceStep())
        
        if (updatedTask != null) {
            Log.d(TAG, "After: ${updatedTask.phase.label} (step ${updatedTask.currentStep}/${updatedTask.totalSteps})")
            Log.d(TAG, "=== Task Advanced ===\n")
        } else {
            Log.e(TAG, "Failed to update task")
        }
        
        return updatedTask
    }
    
    override suspend fun completeTask(finalResult: String): TaskContext? {
        Log.d(TAG, "=== Completing Task ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to complete")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "Final result: $finalResult")
        Log.d(TAG, "Current phase: ${currentTask.phase.label}")
        
        if (currentTask.phase != TaskPhase.VALIDATION) {
            Log.w(TAG, "Cannot complete task from ${currentTask.phase.label}")
            Log.w(TAG, "Reason: Task completion is only allowed from VALIDATION phase")
            Log.d(TAG, "=== Task Completion Skipped ===\n")
            return currentTask
        }
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.Complete(finalResult))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: ${updatedTask.phase.label}")
            Log.d(TAG, "=== Task Completed ===\n")
        } else {
            Log.e(TAG, "Failed to complete task")
        }
        
        return updatedTask
    }
    
    override suspend fun pauseTask(): TaskContext? {
        Log.d(TAG, "=== Pausing Task ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to pause")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "Before: isActive=${currentTask.isActive}")
        
        if (!currentTask.isActive) {
            Log.w(TAG, "Task is already paused")
            Log.d(TAG, "=== Task Pause Skipped ===\n")
            return currentTask
        }
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.Pause(currentTask.taskId))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: isActive=${updatedTask.isActive}")
            Log.d(TAG, "=== Task Paused ===\n")
        } else {
            Log.e(TAG, "Failed to pause task")
        }
        
        return updatedTask
    }
    
    override suspend fun resumeTask(): TaskContext? {
        Log.d(TAG, "=== Resuming Task ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to resume")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "Before: isActive=${currentTask.isActive}")
        
        if (currentTask.isActive) {
            Log.w(TAG, "Task is already active")
            Log.d(TAG, "=== Task Resume Skipped ===\n")
            return currentTask
        }
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.Resume)
        
        if (updatedTask != null) {
            Log.d(TAG, "After: isActive=${updatedTask.isActive}")
            Log.d(TAG, "=== Task Resumed ===\n")
        } else {
            Log.e(TAG, "Failed to resume task")
        }
        
        return updatedTask
    }
    
    override suspend fun transitionTaskTo(toPhase: TaskPhase): TaskContext? {
        Log.d(TAG, "=== Transitioning Task ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to transition")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "From: ${currentTask.phase.label}")
        Log.d(TAG, "To: ${toPhase.label}")
        
        val canTransition = stateMachine.canTransition(currentTask.phase, toPhase)
        Log.d(TAG, "Can transition: $canTransition")
        
        if (!canTransition) {
            Log.w(TAG, "Transition not allowed! Valid transitions: ${stateMachine.getPossibleTransitions(currentTask.phase).map { it.label }}")
            Log.d(TAG, "=== Task Transition Skipped ===\n")
            return currentTask
        }
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.Transition(toPhase))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: ${updatedTask.phase.label} (step ${updatedTask.currentStep}/${updatedTask.totalSteps})")
            Log.d(TAG, "=== Task Transitioned ===\n")
        } else {
            Log.e(TAG, "Failed to transition task")
        }
        
        return updatedTask
    }
    
    override suspend fun setAwaitingConfirmation(awaiting: Boolean): TaskContext? {
        Log.d(TAG, "=== Setting AwaitingConfirmation ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to set awaiting confirmation")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "Before: ${currentTask.awaitingUserConfirmation}")
        Log.d(TAG, "Setting to: $awaiting")
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.SetAwaitingConfirmation(awaiting))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: ${updatedTask.awaitingUserConfirmation}")
            Log.d(TAG, "=== AwaitingConfirmation Set ===\n")
        } else {
            Log.e(TAG, "Failed to set awaiting confirmation")
        }
        
        return updatedTask
    }
    
    override suspend fun resetAwaitingConfirmation(): TaskContext? {
        Log.d(TAG, "=== Resetting AwaitingConfirmation ===")
        
        val currentTask = taskRepository.getActiveTask() ?: run {
            Log.w(TAG, "No active task to reset awaiting confirmation")
            return null
        }
        
        Log.d(TAG, "Task ID: ${currentTask.taskId}")
        Log.d(TAG, "Before: ${currentTask.awaitingUserConfirmation}")
        
        if (!currentTask.awaitingUserConfirmation) {
            Log.d(TAG, "Awaiting confirmation is already false")
            Log.d(TAG, "=== AwaitingConfirmation Reset Skipped ===\n")
            return currentTask
        }
        
        val updatedTask = taskRepository.updateTask(currentTask.taskId, TaskAction.SetAwaitingConfirmation(false))
        
        if (updatedTask != null) {
            Log.d(TAG, "After: ${updatedTask.awaitingUserConfirmation}")
            Log.d(TAG, "=== AwaitingConfirmation Reset ===\n")
        } else {
            Log.e(TAG, "Failed to reset awaiting confirmation")
        }
        
        return updatedTask
    }
}
