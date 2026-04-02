package com.example.aiadventchallenge.domain.model

import android.util.Log

class TaskStateMachine {

    private val TAG = "TaskStateMachine"
    
    companion object {
        private val VALID_TRANSITIONS = mapOf(
            TaskPhase.PLANNING to setOf(
                TaskPhase.EXECUTION
            ),
            TaskPhase.EXECUTION to setOf(
                TaskPhase.VALIDATION,
                TaskPhase.PLANNING
            ),
            TaskPhase.VALIDATION to setOf(
                TaskPhase.DONE,
                TaskPhase.EXECUTION,
                TaskPhase.PLANNING
            ),
            TaskPhase.DONE to emptySet()
        )
    }

    fun transition(current: TaskContext, action: TaskAction): TaskContext {
        Log.d(TAG, "=== State Machine Transition ===")
        Log.d(TAG, "Current state: ${current.phase.label} (step ${current.currentStep}/${current.totalSteps})")
        Log.d(TAG, "Action: $action")

        val result = when (action) {
            is TaskAction.Create -> {
                Log.d(TAG, "Action type: Create")
                TaskContext.create(action.query, action.profile)
            }

            is TaskAction.UpdatePhase -> {
                Log.d(TAG, "Action type: UpdatePhase to ${action.phase.label}")
                if (canTransition(current.phase, action.phase)) {
                    Log.d(TAG, "Transition allowed")
                    current.copyWithPhase(action.phase)
                } else {
                    Log.w(TAG, "Transition not allowed")
                    current
                }
            }

            is TaskAction.AdvanceStep -> {
                Log.d(TAG, "Action type: AdvanceStep (steps: ${action.steps})")
                if (current.canAdvance) {
                    val newStep = current.currentStep + action.steps
                    Log.d(TAG, "Advancing from step ${current.currentStep} to $newStep (total: ${current.totalSteps})")
                    if (newStep > current.totalSteps) {
                        Log.d(TAG, "Step exceeds total, advancing to next phase")
                        advanceToNextPhase(current)
                    } else {
                        Log.d(TAG, "Staying in same phase, moving to step $newStep")
                        current.copyWithNewStep(newStep)
                    }
                } else {
                    Log.w(TAG, "Cannot advance: isCompleted=${current.isCompleted}, currentStep=${current.currentStep}, totalSteps=${current.totalSteps}")
                    current
                }
            }

            is TaskAction.UpdatePlan -> {
                Log.d(TAG, "Action type: UpdatePlan (${action.plan.size} steps)")
                current.updatePlan(action.plan)
            }

            is TaskAction.AddDone -> {
                Log.d(TAG, "Action type: AddDone: ${action.item}")
                current.addDoneItem(action.item)
            }

            is TaskAction.UpdateAction -> {
                Log.d(TAG, "Action type: UpdateAction: ${action.action}")
                current.updateAction(action.action)
            }

            is TaskAction.Pause -> {
                Log.d(TAG, "Action type: Pause")
                current.copy(isActive = false, updatedAt = System.currentTimeMillis())
            }

            is TaskAction.Resume -> {
                Log.d(TAG, "Action type: Resume")
                current.copy(isActive = true, updatedAt = System.currentTimeMillis())
            }

            is TaskAction.Complete -> {
                Log.d(TAG, "Action type: Complete: ${action.finalResult}")
                current.copy(
                    phase = TaskPhase.DONE,
                    currentAction = "Задача выполнена: ${action.finalResult}",
                    awaitingUserConfirmation = false,
                    updatedAt = System.currentTimeMillis()
                )
            }

            is TaskAction.Transition -> {
                Log.d(TAG, "Action type: Transition to ${action.toPhase.label}")
                if (canTransition(current.phase, action.toPhase)) {
                    Log.d(TAG, "Transition allowed")
                    current.copyWithPhase(action.toPhase)
                } else {
                    Log.w(TAG, "Transition not allowed")
                    current
                }
            }

            is TaskAction.SetAwaitingConfirmation -> {
                Log.d(TAG, "Action type: SetAwaitingConfirmation to ${action.awaiting}")
                current.copy(
                    awaitingUserConfirmation = action.awaiting,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }

        Log.d(TAG, "New state: ${result.phase.label} (step ${result.currentStep}/${result.totalSteps})")
        Log.d(TAG, "=== State Machine Transition End ===\n")
        return result
    }

    fun canTransition(from: TaskPhase, to: TaskPhase): Boolean {
        if (from == to) return true
        return VALID_TRANSITIONS[from]?.contains(to) == true
    }

    fun getNextPhase(current: TaskPhase): TaskPhase? {
        return VALID_TRANSITIONS[current]?.firstOrNull { it.position > current.position }
    }

    fun canAdvanceToNextPhase(current: TaskContext): Boolean {
        return current.currentStep >= current.totalSteps && 
               current.phase != TaskPhase.DONE &&
               getNextPhase(current.phase) != null
    }

    private fun advanceToNextPhase(current: TaskContext): TaskContext {
        val nextPhase = getNextPhase(current.phase) ?: return current
        return current.copyWithPhase(nextPhase)
    }

    fun getPossibleTransitions(from: TaskPhase): Set<TaskPhase> {
        return VALID_TRANSITIONS[from] ?: emptySet()
    }
}
