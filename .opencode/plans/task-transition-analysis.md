# Анализ логики переходов EXECUTION → VALIDATION → DONE

## Допустимые переходы (из TaskStateMachine.kt)

```
PLANNING    → EXECUTION
EXECUTION    → VALIDATION, PLANNING
VALIDATION    → DONE, EXECUTION, PLANNING
DONE         → (финальная фаза)
```

---

## Текущая логика (ChatViewModel.kt строки 421-500)

### Порядок проверки

1. **ПРИОРИТЕТ 1:** Обработка подтверждения пользователя (`awaitingConfirmation`)
2. **ПРИОРИТЕТ 2:** Явный переход через `transitionTo`
3. **ПРИОРИТЕТ 3:** PLANNING - проверка готовности плана
4. **ПРИОРИТЕТ 4:** Шаг завершён (`stepCompleted`)

---

## Критическая проблема из логов

### Сценарий 1: VALIDATION → DONE (19:26:54)

**Что произошло:**
```
Phase: Проверка (1/1)
awaitingConfirmation: false

AI возвращает:
  stepCompleted: true
  transitionTo: DONE
  nextAction: "Задача завершена, программа передана пользователю"

Система:
  → Входит в блок ПРИОРИТЕТ 4: if (aiResponse.stepCompleted)
  → Проверяет: currentTask.isCompleted && phase != DONE
  → Условие ЛОЖНО (isCompleted=true, но логика не обрабатывает)
  → НЕ переходит в DONE, устанавливает awaitingConfirmation=true ❌
```

**Корневая причина:**
Когда `awaitingConfirmation == false`, система должна обрабатывать `transitionTo: DONE` ПЕРЕД `stepCompleted`, но этого не происходит потому что:
- Блок `transitionTo` (строки 456-465) стоит ПЕРЕД `stepCompleted` (строки 477-500)
- Однако при `stepCompleted: true` система входит в блок stepCompleted
- Блок `transitionTo` НЕ выполняется когда `stepCompleted: true`

---

## Детальный анализ каждого блока

### Блок 1: Обработка подтверждения (строки 429-453)

```kotlin
if (currentTask.awaitingUserConfirmation) {
    val isAffirmative = parseAffirmativeResponse(aiResponse)
    if (isAffirmative) {
        val nextPhase = TaskStateMachine().getNextPhase(currentTask.phase)
        if (nextPhase != null) {
            transitionTaskTo(nextPhase)
        } else if (currentTask.phase != TaskPhase.DONE) {
            transitionTaskTo(TaskPhase.DONE)  // ✅ Правильно
        }
    } else {
        // Отказ или неясно
        if (currentTask.phase == TaskPhase.VALIDATION) {
            transitionTaskTo(TaskPhase.EXECUTION)  // ✅ Правильно
        } else {
            resetAwaitingConfirmation()
        }
    }
    return
}
```

**Проблема:** Этот блок работает ТОЛЬКО когда `awaitingConfirmation == true`

---

### Блок 2: Явный переход (строки 456-465)

```kotlin
if (aiResponse.transitionTo != null) {
    Log.d(TAG, "Explicit transition requested to: ${aiResponse.transitionTo.label}")
    val stateMachine = TaskStateMachine()
    if (stateMachine.canTransition(currentTask.phase, aiResponse.transitionTo)) {
        transitionTaskTo(aiResponse.transitionTo)
    } else {
        Log.w(TAG, "Transition not allowed: ${aiResponse.transitionTo.label}")
    }
    return
}
```

**Проблема:** Этот блок НИКОГДА не выполняется когда `stepCompleted: true`, потому что:
- Если `stepCompleted: true`, то срабатывает Блок 4 (строки 477-500)
- Но Блок 4 ПЕРЕД Блоком 2
- Это НЕ проблема, потому что `return` в Блоке 2 предотвращает выполнение Блока 4

**ФАКТ:** Блок 2 работает корректно при `stepCompleted: false`

---

### Блок 3: PLANNING проверка (строки 468-474)

```kotlin
if (!currentTask.awaitingUserConfirmation && currentTask.phase == TaskPhase.PLANNING) {
    if (containsPlanKeywords(aiResponse.result)) {
        Log.d(TAG, "PLANNING: Plan presented, awaiting user confirmation")
        setAwaitingConfirmation(true)
        return
    }
}
```

**Проблема:** Этот блок только для PLANNING

---

### Блок 4: Шаг завершён (строки 477-500)

```kotlin
if (aiResponse.stepCompleted) {
    Log.d(TAG, "Step completed: true")
    if (currentTask.isCompleted && currentTask.phase != TaskPhase.DONE) {
        // Все шаги фазы выполнены, но не в DONE - переходим на следующую фазу или DONE
        val nextPhase = TaskStateMachine().getNextPhase(currentTask.phase)
        if (nextPhase != null) {
            transitionTaskTo(nextPhase)
        } else {
            // Нет следующей фазы - значит финальная, переходим в DONE
            if (currentTask.phase != TaskPhase.DONE) {
                Log.d(TAG, "No next phase, transitioning to DONE")
                transitionTaskTo(TaskPhase.DONE)
            }
        }
    } else if (currentTask.currentStep >= currentTask.totalSteps) {
        Log.d(TAG, "Last step completed, awaiting user confirmation")
        setAwaitingConfirmation(true)
    } else {
        Log.d(TAG, "Advancing to next step: ${currentTask.currentStep + 1}/${currentTask.totalSteps}")
        advanceTask()
    }
}
```

**КРИТИЧЕСКИЕ ПРОБЛЕМЫ:**

1. **Когда `stepCompleted: true` и `isCompleted: false`**:
   - Система проверяет `currentStep >= totalSteps`
   - Устанавливает `awaitingConfirmation = true`
   - При следующем подтверждении пользователя срабатывает Блок 1
   - Это правильная логика для EXECUTION → VALIDATION ✅

2. **Когда `stepCompleted: true` и `isCompleted: true`**:
   - Система вызывает `getNextPhase(currentTask.phase)`
   - Если есть следующая фаза → переходит туда
   - Если нет следующей фазы → переходит в DONE
   - Это правильная логика ✅

---

## РЕАЛЬНАЯ ПРОБЛЕМА

### Сценарий: EXECUTION → VALIDATION → DONE

**Шаг 1: EXECUTION, awaitingConfirmation = false**

AI возвращает:
```
stepCompleted: true
transitionTo: null
```

Система:
- Входит в Блок 4
- Проверяет: `currentStep >= totalSteps`? ✅
- Устанавливает: `awaitingConfirmation = true` ✅
- Это ПРАВИЛЬНО ✅

**Шаг 2: VALIDATION, awaitingConfirmation = true**

Пользователь говорит: "Отлично! Спасибо!"

AI возвращает:
```
stepCompleted: true
transitionTo: DONE
nextAction: "Задача завершена"
```

Система:
- Входит в Блок 1 (awaitingConfirmation)
- Парсит ответ как утвердительный ✅
- Вызывает `getNextPhase(VALIDATION)`
- `VALIDATION` → `DONE` (следующая фаза)
- Переходит в DONE ✅

**Это ПРАВИЛЬНО!**

---

## Но почему не работает в реальности?

Анализирую логи более внимательно:

### Логи 19:26:54 (VALIDATION)

```
Phase: Проверка (1/1)
awaitingConfirmation: false  ← ВАЖНО!

AI возвращает:
  stepCompleted: true
  transitionTo: DONE

Система:
  → НЕ входит в Блок 1 (awaitingConfirmation == false)
  → Проверяет Блок 2 (transitionTo != null) ✅
  → transitionTo: DONE указан
  → Но... почему не переходит?

Ответ: Потому что Блок 4 (stepCompleted) ПЕРЕД Блоком 2!

```

ПРИОРИТЕТ В КОДЕ:
1. awaitingConfirmation
2. transitionTo  ← ДОЛЖЕН БЫТЬ ЗДЕСЬ
3. PLANNING проверка
4. stepCompleted  ← НО ОН ЗДЕСЬ В КОДЕ
```

**В текущем коде:**
- Логика говорит: "ПРИОРИТЕТ 2: Явный переход через transitionTo"
- Но в КОДЕ Блок 4 (stepCompleted) стоит ПЕРЕД Блоком 2
- При `stepCompleted: true` срабатывает Блок 4
- Блок 2 НИКОГДА не выполняется

---

## ВЫВОД

### Реальный порядок проверки в коде:

```kotlin
// Строки 429-453
if (awaitingConfirmation) { ... }

// Строки 468-474
if (PLANNING && план готов) { ... }

// Строки 477-500
if (stepCompleted) { ... }

// Строки 456-465  ← ЭТО НИКОГДА НЕ ВЫПОЛНЯЕТСЯ
if (transitionTo != null) { ... }
```

**ПРОБЛЕМА:** Блок `stepCompleted` стоит ПЕРЕД блоком `transitionTo`!

### Правильный порядок должен быть:

```kotlin
// 1. awaitingConfirmation
if (awaitingConfirmation) { ... }

// 2. transitionTo ← ДОЛЖЕН БЫТЬ ЗДЕСЬ
if (transitionTo != null) { ... }

// 3. PLANNING проверка
if (PLANNING && план готов) { ... }

// 4. stepCompleted
if (stepCompleted) { ... }
```

---

## ТЕСТОВЫЕ СЦЕНАРИИ

### Сценарий 1: EXECUTION → VALIDATION (обычный)

**Состояние:**
- Phase: EXECUTION (1/1)
- awaitingConfirmation: false

**AI возвращает:**
```
stepCompleted: true
transitionTo: null
nextAction: "Программа готова к проверке"
```

**Ожидаемый результат:**
1. awaitingConfirmation = false → пропускаем Блок 1
2. transitionTo = null → пропускаем Блок 2
3. PLANNING = false → пропускаем Блок 3
4. stepCompleted = true → выполняем Блок 4
5. currentStep >= totalSteps → awaitingConfirmation = true ✅

**Текущий результат:**
- awaitingConfirmation = true ✅
- Phase: EXECUTION ✅
- Это ПРАВИЛЬНО ✅

---

### Сценарий 2: VALIDATION → DONE (через подтверждение)

**Состояние:**
- Phase: VALIDATION (1/1)
- awaitingConfirmation: true

**Пользователь:** "Отлично! Спасибо!"

**AI возвращает:**
```
stepCompleted: true
transitionTo: null
nextAction: "Задача завершена"
```

**Ожидаемый результат:**
1. awaitingConfirmation = true → выполняем Блок 1
2. parseAffirmative → true ✅
3. getNextPhase(VALIDATION) → DONE ✅
4. transitionTaskTo(DONE) ✅

**Текущий результат:**
- Phase: DONE ✅
- Это ПРАВИЛЬНО ✅

---

### Сценарий 3: VALIDATION → DONE (через явный transitionTo)

**Состояние:**
- Phase: VALIDATION (1/1)
- awaitingConfirmation: false

**AI возвращает:**
```
stepCompleted: true
transitionTo: DONE
nextAction: "Задача завершена"
```

**Ожидаемый результат:**
1. awaitingConfirmation = false → пропускаем Блок 1
2. transitionTo = DONE → выполняем Блок 2
3. canTransition(VALIDATION, DONE) → true ✅
4. transitionTaskTo(DONE) ✅

**Текущий результат:**
- НИКОГДА не выполняет Блок 2 ❌
- Выполняет Блок 4 (stepCompleted)
- Проверяет isCompleted && phase != DONE
- Устанавливает awaitingConfirmation = true ❌

**Проблема:** Блок 4 (stepCompleted) стоит ПЕРЕД Блоком 2 (transitionTo) в коде!

---

## ИТОГОВЫЙ ВЫВОД

### Проблема в коде:

**Фактический порядок проверки в ChatViewModel.kt:**

```
Строка 429: if (awaitingConfirmation) { ... }
Строка 468: if (PLANNING && план готов) { ... }
Строка 477: if (stepCompleted) { ... }
Строка 456: if (transitionTo != null) { ... }  ← НИКОГДА НЕ ВЫПОЛНЯЕТСЯ
```

**Правильный порядок должен быть:**

```
Строка 429: if (awaitingConfirmation) { ... }
Строка 456: if (transitionTo != null) { ... }  ← ДОЛЖЕН БЫТЬ ЗДЕСЬ
Строка 468: if (PLANNING && план готов) { ... }
Строка 477: if (stepCompleted) { ... }
```

### Почему это не было исправлено?

В предыдущем исправлении я ПЕРЕСТАВИЛ блоки в правильном порядке, но возможно:
1. Изменения не сохранились корректно
2. Или файл не был скомпилирован
3. Или текущая версия кода отличается от той, что в логах

---

## ПЛАН ИСПРАВЛЕНИЯ

1. Проверить текущий порядок блоков в ChatViewModel.kt
2. Убедиться, что Блок 2 (transitionTo) стоит ПЕРЕД Блоком 4 (stepCompleted)
3. Если нет — переставить их
4. Протестировать все сценарии
5. Убедиться, что логи подтверждают исправления

---

## СПИСОК ПРОВЕРОК

- [ ] Проверить порядок блоков в ChatViewModel.kt (строки 421-500)
- [ ] Убедиться, что Блок 2 (transitionTo) стоит ПЕРЕД Блоком 4 (stepCompleted)
- [ ] Тест: EXECUTION → VALIDATION через stepCompleted
- [ ] Тест: VALIDATION → DONE через подтверждение
- [ ] Тест: VALIDATION → DONE через явный transitionTo
- [ ] Проверить логи после исправления
