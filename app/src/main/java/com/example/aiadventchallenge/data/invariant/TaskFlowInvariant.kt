package com.example.aiadventchallenge.data.invariant

import com.example.aiadventchallenge.domain.model.*

class TaskFlowInvariant : Invariant {
    override val id = "task_flow_control"
    override val category = InvariantCategory.TASK_FLOW
    override val description = "Контролируемые переходы между фазами задачи"
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

    private fun hasDetailedSolutionInPlanning(content: String): Boolean {
        val structuredListPattern = Regex(
            """(Пн|Вт|Ср|Чт|Пт|Сб|Вс|Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье|День|Шаг|Этап)\s*[:：]\s*[^,\n]+\d+""",
            RegexOption.IGNORE_CASE
        )
        val hasStructuredList = structuredListPattern.containsMatchIn(content)

        val specificValuesPattern = Regex(
            """\d+\s*(г|кг|мл|мг|мкг|грамм|килограмм|раз|подход|повторение|упражнение|мин|час|день|неделя|мес)""",
            RegexOption.IGNORE_CASE
        )
        val hasSpecificValues = specificValuesPattern.containsMatchIn(content)

        val hasQuestions = content.contains("?") ||
                          content.lowercase().contains("какую") ||
                          content.lowercase().contains("какой") ||
                          content.lowercase().contains("сколько") ||
                          content.lowercase().contains("есть ли") ||
                          content.lowercase().contains("можете") ||
                          content.lowercase().contains("хотите") ||
                          content.lowercase().contains("желаете") ||
                          content.lowercase().contains("предпочитаете")

        return (hasStructuredList || hasSpecificValues) && !hasQuestions
    }
}

class PlanningPhaseInvariant : Invariant {
    override val id = "planning_phase_restrictions"
    override val category = InvariantCategory.TASK_FLOW
    override val description = "Запрет детальных решений в фазе PLANNING (универсальный)"
    override val priority = InvariantPriority.HARD
    override val isEnabled = true

    override fun validate(
        content: String,
        context: TaskContext?,
        role: MessageRole
    ): InvariantViolation? {
        if (role != MessageRole.ASSISTANT || context?.phase != TaskPhase.PLANNING) {
            return null
        }

        if (hasDetailedSolutionInPlanning(content)) {
            return InvariantViolation(
                invariantId = id,
                invariantDescription = description,
                reason = """
                    В фазе PLANNING запрещено выдавать готовое решение с конкретными значениями и деталями.

                    PLANNING предназначена для:
                    - Сбора информации и требований
                    - Уточнения деталей задачи
                    - Задавания вопросов пользователю

                    Детальное решение должно создаваться в фазе EXECUTION.
                """.trimIndent(),
                suggestion = """
                    В фазе PLANNING нужно:
                    1. Задать вопросы пользователю (цель, ограничения, ресурсы, предпочтения)
                    2. Собрать информацию итеративно
                    3. Представить резюме требований
                    4. Спросить подтверждение для перехода в EXECUTION

                    НЕЛЬЗЯ: выдавать готовый план/программу/протокол с конкретными значениями (граммы, подходы, повторения, дни с упражнениями и т.д.).
                """.trimIndent(),
                canProceed = false
            )
        }

        return null
    }

    private fun hasDetailedSolutionInPlanning(content: String): Boolean {
        val structuredListPattern = Regex(
            """(Пн|Вт|Ср|Чт|Пт|Сб|Вс|Понедельник|Вторник|Среда|Четверг|Пятница|Суббота|Воскресенье|День|Шаг|Этап)\s*[:：]\s*[^,\n]+\d+""",
            RegexOption.IGNORE_CASE
        )
        val hasStructuredList = structuredListPattern.containsMatchIn(content)

        val specificValuesPattern = Regex(
            """\d+\s*(г|кг|мл|мг|мкг|грамм|килограмм|раз|подход|повторение|упражнение|мин|час|день|неделя|мес)""",
            RegexOption.IGNORE_CASE
        )
        val hasSpecificValues = specificValuesPattern.containsMatchIn(content)

        val hasQuestions = content.contains("?") ||
                          content.lowercase().contains("какую") ||
                          content.lowercase().contains("какой") ||
                          content.lowercase().contains("сколько") ||
                          content.lowercase().contains("есть ли") ||
                          content.lowercase().contains("можете") ||
                          content.lowercase().contains("хотите") ||
                          content.lowercase().contains("желаете") ||
                          content.lowercase().contains("предпочитаете")

        return (hasStructuredList || hasSpecificValues) && !hasQuestions
    }
}
