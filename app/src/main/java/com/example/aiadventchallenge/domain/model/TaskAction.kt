package com.example.aiadventchallenge.domain.model

sealed class TaskAction {
    data class Create(
        val query: String,
        val profile: FitnessProfileType = FitnessProfileType.INTERMEDIATE
    ) : TaskAction()

    data class UpdatePhase(val phase: TaskPhase) : TaskAction()

    data class AdvanceStep(val steps: Int = 1) : TaskAction()

    data class UpdatePlan(val plan: List<String>) : TaskAction()

    data class AddDone(val item: String) : TaskAction()

    data class UpdateAction(val action: String) : TaskAction()

    data class Pause(val taskId: String) : TaskAction()

    data object Resume : TaskAction()

    data class Complete(val finalResult: String = "") : TaskAction()

    data class Transition(val toPhase: TaskPhase) : TaskAction()

    data class SetAwaitingConfirmation(val awaiting: Boolean) : TaskAction()
}
