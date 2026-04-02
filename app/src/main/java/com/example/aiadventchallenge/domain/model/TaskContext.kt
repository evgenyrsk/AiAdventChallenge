package com.example.aiadventchallenge.domain.model

data class TaskContext(
    val taskId: String,
    val query: String,
    val phase: TaskPhase = TaskPhase.PLANNING,
    val currentStep: Int = 1,
    val totalSteps: Int = 1,
    val currentAction: String = "",
    val plan: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val profile: FitnessProfileType = FitnessProfileType.INTERMEDIATE,
    val isActive: Boolean = true,
    val awaitingUserConfirmation: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (totalSteps > 0) {
            currentStep.toFloat() / totalSteps.toFloat()
        } else {
            0f
        }

    val isCompleted: Boolean
        get() = phase == TaskPhase.DONE

    val canAdvance: Boolean
        get() = !isCompleted && currentStep <= totalSteps

    fun copyWithNewStep(step: Int = currentStep + 1): TaskContext {
        return copy(
            currentStep = step,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun copyWithPhase(newPhase: TaskPhase): TaskContext {
        return copy(
            phase = newPhase,
            currentStep = 1,
            awaitingUserConfirmation = false,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun addDoneItem(item: String): TaskContext {
        return copy(
            done = done + item,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun updatePlan(newPlan: List<String>): TaskContext {
        return copy(
            plan = newPlan,
            totalSteps = newPlan.size,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun updateAction(action: String): TaskContext {
        return copy(
            currentAction = action,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun setAwaitingConfirmation(awaiting: Boolean): TaskContext {
        return copy(
            awaitingUserConfirmation = awaiting,
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun create(
            query: String,
            profile: FitnessProfileType = FitnessProfileType.INTERMEDIATE
        ): TaskContext {
            return TaskContext(
                taskId = "task_${System.currentTimeMillis()}",
                query = query,
                phase = TaskPhase.PLANNING,
                currentStep = 1,
                totalSteps = 1,
                currentAction = "Сбор требований и уточнение деталей",
                profile = profile
            )
        }
    }
}
