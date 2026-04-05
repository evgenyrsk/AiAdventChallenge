package com.example.aiadventchallenge.data.invariant

import com.example.aiadventchallenge.domain.model.*

class TaskFlowInvariant : Invariant {
    override val id = "task_flow_control"
    override val category = InvariantCategory.TASK_FLOW
    override val description = "Критические проверки переходов между фазами задачи"
    override val priority = InvariantPriority.HARD
    override val isEnabled = true

    private val stateMachine = TaskStateMachine()

    override fun validate(
        content: String,
        context: TaskContext?,
        role: MessageRole
    ): InvariantViolation? {
        if (role != MessageRole.ASSISTANT) {
            return null
        }

        if (context == null) {
            return null
        }

        if (context.phase == TaskPhase.DONE) {
            return null
        }

        val transitionTo = extractTransitionTo(content)
        if (transitionTo != null) {
            if (!stateMachine.canTransition(context.phase, transitionTo)) {
                val reason = stateMachine.getTransitionReason(context.phase, transitionTo)
                return InvariantViolation(
                    invariantId = id,
                    invariantDescription = description,
                    reason = reason,
                    suggestion = getTransitionSuggestion(context.phase),
                    canProceed = false
                )
            }
        }

        val taskCompleted = content.contains("task_completed: true", ignoreCase = true)
        if (taskCompleted && context.phase != TaskPhase.VALIDATION) {
            val reason = "Завершение задачи (task_completed) разрешено только из фазы VALIDATION"
            return InvariantViolation(
                invariantId = id,
                invariantDescription = "Нельзя завершить задачу без проверки",
                reason = reason,
                suggestion = "Завершите выполнение и перейдите в VALIDATION для проверки",
                canProceed = false
            )
        }

        return null
    }

    private fun extractTransitionTo(content: String): TaskPhase? {
        val patterns = listOf(
            Regex("\\*\\*transition_to\\*\\*\\s*:\\s*([^\n]+)", RegexOption.IGNORE_CASE),
            Regex("transition_to\\s*:\\s*([^\n]+)", RegexOption.IGNORE_CASE),
            Regex("transitionTo\\s*:\\s*([^\n]+)", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) {
                val phaseName = match.groupValues.get(1).trim().removeSurrounding("\"").uppercase()
                return try {
                    TaskPhase.valueOf(phaseName)
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun getTransitionSuggestion(currentPhase: TaskPhase): String {
        val possibleTransitions = stateMachine.getPossibleTransitions(currentPhase)
        return if (possibleTransitions.isEmpty()) {
            "Нет допустимых переходов из фазы ${currentPhase.label} (финальная фаза)"
        } else {
            "Допустимые переходы: ${possibleTransitions.joinToString { it.label }}"
        }
    }
}
