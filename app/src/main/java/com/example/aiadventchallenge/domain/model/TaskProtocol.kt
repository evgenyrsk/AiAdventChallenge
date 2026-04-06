package com.example.aiadventchallenge.domain.model

/**
 * Единый протокол взаимодействия между пользователем и LLM для задач
 *
 * Все правила переходов, ограничений и сценариев описаны здесь.
 * TaskPromptBuilder использует этот протокол для построения промптов.
 */
object TaskProtocol {

    // ============================================================
    // ОПИСАНИЕ ФАЗ
    // ============================================================

    data class PhaseDefinition(
        val phase: TaskPhase,
        val label: String,
        val description: String,
        val allowedTransitions: Set<TaskPhase>,
        val restrictions: List<String>,
        val capabilities: List<String>
    )

    val PHASE_DEFINITIONS: Map<TaskPhase, PhaseDefinition> = mapOf(
        TaskPhase.PLANNING to PhaseDefinition(
            phase = TaskPhase.PLANNING,
            label = "Планирование",
            description = "Сбор требований и утверждение плана работы",
            allowedTransitions = setOf(TaskPhase.EXECUTION),
            restrictions = listOf(
                "ЗАПРЕЩЕНО выдавать структурированный результат (дни недели, шаги, элементы с деталями)",
                "ЗАПРЕЩЕНО включать конкретные значения: кг, раз, повторений, грамм, минут",
                "ЗАПРЕЩЕНО составлять детальный план/программу/протокол/режим",
                "ЗАПРЕЩЕНО решать задачу до EXECUTION",
                "ЗАПРЕЩЕНО предлагать 'готовое решение' даже в кратком виде",
                "ЗАПРЕЩЕНО использовать форматы: 'Пн: ..., Вт: ...', 'Шаг 1: ..., Шаг 2: ...'"
            ),
            capabilities = listOf(
                "Задавать вопросы для сбора информации (цель, ограничения, ресурсы)",
                "Уточнять детали задачи итеративно",
                "Представлять краткое резюме собранных требований",
                "Спрашивать подтверждение для перехода в EXECUTION"
            )
        ),

        TaskPhase.EXECUTION to PhaseDefinition(
            phase = TaskPhase.EXECUTION,
            label = "Выполнение",
            description = "Создание программ, протоколов и рекомендаций",
            allowedTransitions = setOf(TaskPhase.VALIDATION, TaskPhase.PLANNING),
            restrictions = listOf(
                "ЗАПРЕЩЕНО использовать transition_to для переходов на VALIDATION или DONE",
                "ЗАПРЕЩЕНО использовать фразы в next_action: 'Начать фазу', 'Переход к', 'Проверяем'",
                "ЗАПРЕЩЕНО упоминать результаты валидации (проверено, проверил, прошел проверку)",
                "ЗАПРЕЩЕНО явно переходить на VALIDATION через transition_to"
            ),
            capabilities = listOf(
                "Создавать детальный план/программу/протокол",
                "Структурировать по дням/неделям",
                "Добавлять конкретные упражнения, подходы, повторения, установки, инструкции",
                "Включать рекомендации по питанию, режиму, восстановлению",
                "Учитывать итеративные изменения по запросу пользователя",
                "Использовать transition_to ТОЛЬКО для явного возврата на PLANNING, если уточняются или меняются требования"
            )
        ),

        TaskPhase.VALIDATION to PhaseDefinition(
            phase = TaskPhase.VALIDATION,
            label = "Проверка",
            description = "Ревью результата и возврат при необходимости",
            allowedTransitions = setOf(TaskPhase.DONE, TaskPhase.EXECUTION, TaskPhase.PLANNING),
            restrictions = listOf(
                "ЗАПРЕЩЕНО использовать transition_to: DONE (используй task_completed: true)",
                "ЗАПРЕЩЕНО использовать step_completed: true (используй task_completed: true)",
                "ЗАПРЕЩЕНО завершать задачу при несогласии пользователя"
            ),
            capabilities = listOf(
                "Получать фидбек от пользователя",
                "Возвращаться на EXECUTION при несогласии с исправлениями",
                "Завершать задачу через task_completed: true при утверждении",
                "Возвращаться на PLANNING по запросу пользователя",
                "Распознавать новые задачи и возвращать NEW_TASK"
            )
        ),

        TaskPhase.DONE to PhaseDefinition(
            phase = TaskPhase.DONE,
            label = "Завершено",
            description = "Задача выполнена",
            allowedTransitions = emptySet(),
            restrictions = listOf(
                "ЗАПРЕЩЕНО любые переходы из фазы DONE"
            ),
            capabilities = listOf(
                "Подтверждать что задача выполнена",
                "Предлагать новые задачи",
                "Распознавать новые задачи и возвращать NEW_TASK"
            )
        )
    )

    // ============================================================
    // ПРАВИЛА ПЕРЕХОДОВ
    // ============================================================

    val VALID_TRANSITIONS: Map<TaskPhase, Set<TaskPhase>> = mapOf(
        TaskPhase.PLANNING to setOf(TaskPhase.EXECUTION),
        TaskPhase.EXECUTION to setOf(TaskPhase.VALIDATION, TaskPhase.PLANNING),
        TaskPhase.VALIDATION to setOf(TaskPhase.DONE, TaskPhase.EXECUTION, TaskPhase.PLANNING),
        TaskPhase.DONE to emptySet()
    )

    val FORBIDDEN_TRANSITIONS: Map<Pair<TaskPhase, TaskPhase>, String> = mapOf(
        (TaskPhase.PLANNING to TaskPhase.DONE) to "Нельзя завершить задачу пропустив выполнение и проверку",
        (TaskPhase.EXECUTION to TaskPhase.DONE) to "Нельзя завершить задачу без проверки",
        (TaskPhase.PLANNING to TaskPhase.VALIDATION) to "Нельзя перейти к проверке пропустив выполнение",
        (TaskPhase.DONE to TaskPhase.PLANNING) to "Нельзя сделать переход из завершенной задачи"
    )

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ
    // ============================================================

    fun getPhaseDefinition(phase: TaskPhase): PhaseDefinition? {
        return PHASE_DEFINITIONS[phase]
    }

    fun canTransition(from: TaskPhase, to: TaskPhase): Boolean {
        return VALID_TRANSITIONS[from]?.contains(to) == true || from == to
    }

    fun getForbiddenTransitionReason(from: TaskPhase, to: TaskPhase): String? {
        return FORBIDDEN_TRANSITIONS[Pair(from, to)]
    }

    fun getPossibleTransitions(from: TaskPhase): Set<TaskPhase> {
        return VALID_TRANSITIONS[from] ?: emptySet()
    }

    fun getNextPhase(current: TaskPhase): TaskPhase? {
        return VALID_TRANSITIONS[current]?.firstOrNull { it.position > current.position }
    }
}
