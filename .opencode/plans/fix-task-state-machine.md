# План исправления конечного автомата задач

## Проблема 1: VALIDATION → DONE не происходит

### Корневая причина
Логика в `ChatViewModel.kt` (строки 461-490) проверяет `stepCompleted` ПЕРЕД `transitionTo`, из-за чего явный переход `transitionTo: DONE` игнорируется.

### Решение
Перестроить логику с приоритетом:
1. **Приоритет 1:** Проверка `awaitingUserConfirmation` (подтверждение пользователя)
2. **Приоритет 2:** Явный `transitionTo` (переход к другой фазе)
3. **Приоритет 3:** `stepCompleted` (завершение шага)

### Изменения в файле `ChatViewModel.kt`

#### Блок `TaskIntent.CONTINUE_TASK/CLARIFICATION` (строки 421-497)

**Текущая структура:**
```
if (awaitingConfirmation) { ... }
if (!awaitingConfirmation && PLANNING) { ... }
if (stepCompleted) { ... }
else { if (transitionTo != null) { ... } }
```

**Новая структура:**
```
if (awaitingConfirmation) { ... }

// ПРИОРИТЕТ 1: Явный переход через transitionTo
if (transitionTo != null) {
    // Проверка canTransition
    // Выполняем переход
    return
}

// ПРИОРИТЕТ 2: PLANNING - проверка готовности плана
if (!awaitingConfirmation && PLANNING && containsPlanKeywords) {
    setAwaitingConfirmation(true)
    return
}

// ПРИОРИТЕТ 3: Шаг завершён
if (stepCompleted) {
    if (isCompleted && phase != DONE) {
        nextPhase = getNextPhase()
        if (nextPhase != null) transitionTaskTo(nextPhase)
        else transitionTaskTo(DONE) // ← Добавлено
    } else if (currentStep >= totalSteps) {
        setAwaitingConfirmation(true)
    } else {
        advanceTask()
    }
}
```

#### Блок подтверждения в `awaitingConfirmation` (строки 428-450)

**Добавить автоматический переход в DONE:**
```kotlin
if (isAffirmative) {
    val nextPhase = TaskStateMachine().getNextPhase(currentTask.phase)
    if (nextPhase != null) {
        transitionTaskTo(nextPhase)
    } else if (currentTask.phase != TaskPhase.DONE) {
        // Нет следующей фазы - финальная, переходим в DONE
        transitionTaskTo(TaskPhase.DONE)
    }
}
```

---

## Проблема 2: Новая задача не создаётся корректно

### Наблюдение
Когда пользователь говорит "Давай теперь сформируем протокол питания", это должен быть `task_intent: NEW_TASK`, но AI возвращает `CONTINUE_TASK`.

### Текущее поведение (неверное)
- `stepCompleted: false`
- Система интерпретирует как "отверждение" → возврат в EXECUTION
- Питание добавляется к текущей задаче

### Желаемое поведение
- AI должен вернуть `task_intent: NEW_TASK`
- `new_task_query: "Сформировать протокол питания для рекомпозиции"`
- Система создаёт новую задачу

### Решение
**Изменение в промпте для VALIDATION фазы** (не требует изменения кода):

В `TaskPromptBuilder.kt` в инструкциях для `TaskPhase.VALIDATION` (строки 607-649) добавить:

```kotlin
TaskPhase.VALIDATION -> """
    ================================================================
    🎯 ФАЗА: VALIDATION (Проверка)
    ================================================================

    ЦЕЛЬ: Ревью результата, возврат при необходимости

    ТВОИ ЗАДАЧИ:
    1. Получи фидбек от пользователя
       - "Всё устраивает?" → DONE
       - "Не нравится..." → EXECUTION (с исправлениями)
       - "Измени X" → EXECUTION (с изменением)
       - "Слишком сложно" → EXECUTION (с упрощением)

    2. Если пользователь утверждает:
       - Подтверди завершение задачи
       - Переход на DONE

    3. Если пользователь НЕ утверждает:
       - Вернись на EXECUTION
       - Примени изменения
       - Представь исправленную версию

    4. ВОЗВРАТ НА PLANNING если нужно:
       - "Давай уточним требования сначала"
       - "Пересмотрим план"

    5. НОВАЯ ЗАДАЧА:
       Если пользователь запрашивает РАБОТА, ОТЛИЧНАЯ ОТ текущей:
       - "Составь протокол питания"
       - "Хочу план питания"
       - "Теперь давай про питание"
       - task_intent: NEW_TASK ✅
       - new_task_query: <текст новой задачи>
       - НЕ используй CONTINUE_TASK для новой работы!

    ПРИМЕР ПРАВИЛЬНОГО ДИАЛОГА:
    L: Всё устраивает? Можно завершать?
    U: Да, отлично! ✅ (переход на DONE)

    ПРИМЕР НОВОЙ ЗАДАЧИ:
    U: Давай теперь сформируем протокол питания
    L: Отлично, это новая задача. Создаю протокол питания...
    → task_intent: NEW_TASK ✅
    new_task_query: "Сформировать протокол питания для рекомпозиции"

    ДОПУСТИМЫЕ ПЕРЕХОДЫ: DONE, EXECUTION, PLANNING
"""
```

---

## Проверка

После реализации изменений:
1. VALIDATION → DONE должен происходить при явном `transitionTo: DONE`
2. Новая задача должна создаваться корректно через `NEW_TASK`
3. Все существующие переходы должны работать

---

## Статус

- [ ] Изменить логику в `ChatViewModel.kt` (строки 421-497)
- [ ] Добавить автоматический переход в DONE при отсутствии следующей фазы
- [ ] Обновить промпт для VALIDATION (опционально, улучшает детерминированность)
- [ ] Протестировать сценарий VALIDATION → DONE
- [ ] Протестировать создание новой задачи
