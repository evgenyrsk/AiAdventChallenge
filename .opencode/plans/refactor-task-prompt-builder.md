# План рефакторинга TaskPromptBuilder и TaskStateMachine

## Обзор

Цель: устранить дублирование промптов и сделать протокол взаимодействия прозрачным и четким, сохраняя существующую функциональность.

## Текущие проблемы

### 1. Дублирование
- Инструкции для `EXECUTION` определены дважды (строки 731-887 и 889-1072 в TaskPromptBuilder.kt)
- Правила переходов дублируются в `TaskStateMachine`, промптах и документации
- Одни и те же сценарии описаны в разных частях кода

### 2. Промпты слишком сложные
- `buildSystemPrompt()` содержит ~500+ строк
- Много вложенных правил и примеров
- Нет четкой структуры для LLM

### 3. Протокол неявный
- Правила разбросаны по разным местам
- Отсутствует единый источник правды
- LLM может интерпретировать инструкции по-разному

## Решение

### Шаг 1: Создать `TaskProtocol.kt` - единый источник правды

**Местоположение:** `app/src/main/java/com/example/aiadventchallenge/domain/model/TaskProtocol.kt`

**Назначение:** Централизованное хранение всех правил протокола взаимодействия между пользователем и LLM.

**Структура:**

```kotlin
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
                "Добавлять конкретные упражнения, подходы, повторения",
                "Включать рекомендации по питанию, режиму, восстановлению",
                "Учитывать итеративные изменения по запросу пользователя",
                "Использовать transition_to ТОЛЬКО для явного возврата на PLANNING"
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
        (TaskPhase.DONE to TaskPhase.PLANNING) to "Нельзя перейти из завершенной задачи"
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
```

### Шаг 2: Рефакторинг TaskPromptBuilder

**Цель:** Упростить и структурировать промпты, используя TaskProtocol.

**Изменения:**

1. **Удалить дублирование** - удалить второй блок инструкций для EXECUTION (строки 889-1072)

2. **Использовать TaskProtocol** - заменить жестко закодированные правила на данные из TaskProtocol:

```kotlin
private fun buildPhaseSpecificPrompt(ctx: TaskContext, fitnessProfile: FitnessProfileType): String {
    val phaseDef = TaskProtocol.getPhaseDefinition(ctx.phase)
        ?: return ""

    val transitions = TaskProtocol.getPossibleTransitions(ctx.phase)
    val nextPhase = TaskProtocol.getNextPhase(ctx.phase)

    return """
    ====================================================================
    🎯 ФАЗА: ${phaseDef.label}
    ====================================================================

    ОПИСАНИЕ: ${phaseDef.description}

    ====================================================================
    🚨 СТРОГО ЗАПРЕЩЕНО
    ====================================================================

    ${phaseDef.restrictions.joinToString("\n") { "- $it" }}

    ====================================================================
    ✅ ДОПУСТИМЫЕ ДЕЙСТВИЯ
    ====================================================================

    ${phaseDef.capabilities.joinToString("\n") { "- $it" }}

    ====================================================================
    🔄 ДОПУСТИМЫЕ ПЕРЕХОДЫ
    ====================================================================

    Следующие фазы: ${transitions.joinToString(", ") { it.label }}
    Следующая фаза: ${nextPhase?.label ?: "Нет (финальная фаза)"}

    ====================================================================
    """.trimIndent()
}
```

3. **Создать метод buildScenariosPrompt()** - генерировать примеры сценариев из TaskProtocol:

```kotlin
private fun buildScenariosPrompt(phase: TaskPhase): String {
    val scenarios = TaskProtocol.SCENARIOS.filter { it.fromPhase == phase }

    return """
    ====================================================================
    📋 СЦЕНАРИИ ВЗАИМОДЕЙСТВИЯ
    ====================================================================

    ${scenarios.joinToString("\n\n") { scenario ->
        """
        Сценарий: ${scenario.name}
        Пользователь: "${scenario.userInputPattern}"
        Ожидаемый ответ: "${scenario.expectedLlmResponse}"

        Действия:
        - task_intent: ${scenario.llmActions.taskIntent}
        - step_completed: ${scenario.llmActions.stepCompleted}
        - task_completed: ${scenario.llmActions.taskCompleted}
        - transition_to: ${scenario.llmActions.transitionTo?.label ?: "null"}
        - next_action: "${scenario.llmActions.nextAction}"
        """.trimIndent()
    }}
    ====================================================================
    """.trimIndent()
}
```

4. **Создать метод buildResponseFormatPrompt()** - генерировать формат ответа из TaskProtocol:

```kotlin
private fun buildResponseFormatPrompt(phase: TaskPhase): String {
    val responseFormat = TaskProtocol.RESPONSE_FORMAT
    val allowedFields = responseFormat.fields.filter {
        it.prohibitedPhases == null || phase !in it.prohibitedPhases
    }

    return """
    ====================================================================
    📤 ФОРМАТ ОТВЕТА
    ====================================================================

    ${allowedFields.joinToString("\n") { field ->
        """
        ${field.name}:
        - Обязательно: ${if (field.required) "Да" else "Нет"}
        - Описание: ${field.description}
        ${field.allowedValues?.let { "- Допустимые значения: ${it.joinToString(", ")}\n" } ?: ""}
        """.trimIndent()
    }}

    ====================================================================
    """.trimIndent()
}
```

### Шаг 3: Обновить TaskStateMachine

**Цель:** Интегрировать TaskProtocol с TaskStateMachine для устранения дублирования.

**Изменения:**

```kotlin
class TaskStateMachine {

    private val TAG = "TaskStateMachine"

    // Удалить VALID_TRANSITIONS - использовать TaskProtocol
    // companion object { ... }

    fun transition(current: TaskContext, action: TaskAction): TaskContext {
        // ... существующая логика ...

        // Использовать TaskProtocol для проверки переходов
        when (action) {
            is TaskAction.UpdatePhase -> {
                if (!TaskProtocol.canTransition(current.phase, action.phase)) {
                    val reason = TaskProtocol.getForbiddenTransitionReason(
                        current.phase,
                        action.phase
                    )
                    Log.w(TAG, "Transition denied: $reason")
                    return current
                }
                // ...
            }
        }
    }

    fun canTransition(from: TaskPhase, to: TaskPhase): Boolean {
        return TaskProtocol.canTransition(from, to)
    }

    fun getNextPhase(current: TaskPhase): TaskPhase? {
        return TaskProtocol.getNextPhase(current)
    }

    fun getPossibleTransitions(from: TaskPhase): Set<TaskPhase> {
        return TaskProtocol.getPossibleTransitions(from)
    }

    fun getTransitionReason(from: TaskPhase, to: TaskPhase): String {
        return TaskProtocol.getForbiddenTransitionReason(from, to)
            ?: "Недопустимый переход: ${from.label} → ${to.label}"
    }
}
```

### Шаг 4: Обновить ChatAgent

**Цель:** Использовать TaskProtocol для построения промптов.

**Изменения:**

```kotlin
fun buildRequestConfigWithTask(
    taskContext: TaskContext?,
    fitnessProfile: FitnessProfileType = FitnessProfileType.INTERMEDIATE,
    userInput: String? = null
): RequestConfig {
    if (taskContext == null) {
        // ... существующая логика ...
    }

    // Использовать TaskPromptBuilder с TaskProtocol
    val taskPrompt = TaskPromptBuilder.buildSystemPrompt(taskContext, fitnessProfile)

    val protocolPrompt = buildProtocolPrompt(taskContext.phase)

    val invariantsPrompt = buildInvariantsPrompt()
    val enhancedPrompt = """
    $taskPrompt

    $protocolPrompt

    $invariantsPrompt
    """.trimIndent()

    return RequestConfig(
        systemPrompt = enhancedPrompt
    )
}

private fun buildProtocolPrompt(phase: TaskPhase): String {
    val phaseDef = TaskProtocol.getPhaseDefinition(phase) ?: return ""

    return """
    ================================================================================
    📋 ПРОТОКОЛ ВЗАИМОДЕЙСТВИЯ - ${phaseDef.label.toUpperCase()}
    ================================================================================

    Описание фазы: ${phaseDef.description}

    Допустимые переходы: ${TaskProtocol.getPossibleTransitions(phase).joinToString { it.label }}
    """.trimIndent()
}
```

### Шаг 5: Обновить тесты

**Цель:** Обновить существующие тесты для работы с TaskProtocol.

**Изменения:**

```kotlin
class TaskStateMachineTest {

    private val stateMachine = TaskStateMachine()

    @Test
    fun `test allowed transition PLANNING to EXECUTION`() {
        val result = TaskProtocol.canTransition(
            TaskPhase.PLANNING,
            TaskPhase.EXECUTION
        )
        assertTrue("Expected Allowed result", result)
    }

    @Test
    fun `test forbidden transition PLANNING to DONE`() {
        val result = TaskProtocol.canTransition(
            TaskPhase.PLANNING,
            TaskPhase.DONE
        )
        assertFalse("Expected Denied result", result)

        val reason = TaskProtocol.getForbiddenTransitionReason(
            TaskPhase.PLANNING,
            TaskPhase.DONE
        )
        assertNotNull("Reason should exist", reason)
        assertTrue("Reason should mention skipping", "выполнение" in reason ?: "")
    }
}
```

## Порядок выполнения

1. **Шаг 1:** Создать `TaskProtocol.kt`
2. **Шаг 2:** Обновить `TaskPromptBuilder.kt` (использовать TaskProtocol)
3. **Шаг 3:** Обновить `TaskStateMachine.kt` (интегрировать TaskProtocol)
4. **Шаг 4:** Обновить `ChatAgent.kt` (использовать протокол)
5. **Шаг 5:** Обновить тесты
6. **Шаг 6:** Проверить работоспособность и backward compatibility

## Результаты

После выполнения плана будет достигнуто:

1. **Устранено дублирование:** Все правила определены в одном месте
2. **Упрощены промпты:** Четкая структура, меньше объема
3. **Прозрачный протокол:** Единый источник правды для всех компонентов
4. **Легкая поддержка:** Изменение правил в одном месте обновляет всё
5. **Проверяемость:** Тесты используют TaskProtocol напрямую
