# Тестовые сценарии для проверки логики переходов

## Цель
Проверить, что логика переходов между фазами работает корректно после исправлений.

---

## Сценарий 1: EXECUTION → VALIDATION → DONE (обычный)

### Шаг 1: EXECUTION → VALIDATION

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
```
=== Task Intent Handler ===
Step completed: true
Last step completed, awaiting user confirmation
=== Setting AwaitingConfirmation ===
Task ID: task_...
From: false
To: true
```

**Финальное состояние:**
- Phase: EXECUTION (1/1)
- awaitingConfirmation: true

---

### Шаг 2: VALIDATION → DONE (подтверждение)

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
```
=== Task Intent Handler ===
User confirmed - transitioning to next phase
=== Transitioning Task ===
Task ID: task_...
From: Проверка (step 1/1)
To: Завершено
Can transition: true
=== Task Transitioned ===
```

**Финальное состояние:**
- Phase: DONE
- awaitingConfirmation: false

---

## Сценарий 2: VALIDATION → DONE (через явный transitionTo)

### Шаг 1: VALIDATION с явным переходом

**Состояние:**
- Phase: VALIDATION (1/1)
- awaitingConfirmation: false

**AI возвращает:**
```
stepCompleted: true
transitionTo: DONE
nextAction: "Задача завершена, программа передана пользователю"
```

**Ожидаемый результат:**
```
=== Task Intent Handler ===
Explicit transition requested to: DONE  ← НОВЫЙ ЛОГ!
=== Transitioning Task ===
Task ID: task_...
From: Проверка (step 1/1)
To: Завершено
Can transition: true
=== Task Transitioned ===
```

**Финальное состояние:**
- Phase: DONE
- awaitingConfirmation: false

---

## Сценарий 3: VALIDATION → EXECUTION (отказ)

### Шаг 1: VALIDATION → EXECUTION

**Состояние:**
- Phase: VALIDATION (1/1)
- awaitingConfirmation: true

**Пользователь:** "Не нравится план на ногах"

**AI возвращает:**
```
stepCompleted: false
transitionTo: null
```

**Ожидаемый результат:**
```
=== Task Intent Handler ===
User rejected or unclear - staying on current phase
VALIDATION: Returning to EXECUTION for corrections
=== Transitioning Task ===
Task ID: task_...
From: Проверка (step 1/1)
To: Выполнение
Can transition: true
=== Task Transitioned ===
```

**Финальное состояние:**
- Phase: EXECUTION
- awaitingConfirmation: false

---

## Сценарий 4: VALIDATION → NEW_TASK (новая задача)

### Шаг 1: Запрос новой задачи

**Состояние:**
- Phase: VALIDATION (1/1)
- awaitingConfirmation: false

**Пользователь:** "Давай теперь сформируем протокол питания"

**AI возвращает:**
```
task_intent: NEW_TASK  ← КРИТИЧЕСКИ ВАЖНО!
new_task_query: "Сформировать протокол питания"
```

**Ожидаемый результат:**
```
=== Task Intent Handler ===
Intent: NEW_TASK
Action: Creating new task
=== Creating Task ===
Task ID: task_...
Query: Сформировать протокол питания
Phase: Планирование (step 1/1)
=== Task Created ===
```

**Финальное состояние:**
- Phase: PLANNING (новой задачи)
- awaitingConfirmation: false

---

## Сценарий 5: PLANNING → EXECUTION

### Шаг 1: PLANNING → EXECUTION

**Состояние:**
- Phase: PLANNING (1/1)
- awaitingConfirmation: true

**Пользователь:** "Да, составь план"

**AI возвращает:**
```
stepCompleted: true
transitionTo: null
```

**Ожидаемый результат:**
```
=== Task Intent Handler ===
User confirmed - transitioning to next phase
=== Transitioning Task ===
Task ID: task_...
From: Планирование (step 1/1)
To: Выполнение
Can transition: true
=== Task Transitioned ===
```

**Финальное состояние:**
- Phase: EXECUTION
- awaitingConfirmation: false

---

## Сценарий 6: PLANNING → EXECUTION (через явный transitionTo)

### Шаг 1: Явный переход

**Состояние:**
- Phase: PLANNING (1/1)
- awaitingConfirmation: false

**AI возвращает:**
```
task_intent: CONTINUE_TASK
stepCompleted: true
transitionTo: EXECUTION
```

**Ожидаемый результат:**
```
=== Task Intent Handler ===
Explicit transition requested to: EXECUTION  ← НОВЫЙ ЛОГ!
=== Transitioning Task ===
Task ID: task_...
From: Планирование (step 1/1)
To: Выполнение
Can transition: true
=== Task Transitioned ===
```

**Финальное состояние:**
- Phase: EXECUTION
- awaitingConfirmation: false

---

## Ключевые логи для проверки

### Новый лог (приоритет transitionTo):
```
D  Explicit transition requested to: DONE
```

### Существующий лог (awaitingConfirmation):
```
D  === Setting AwaitingConfirmation ===
D  Task ID: task_...
D  From: false
D  To: true
```

### Лог перехода:
```
D  === Transitioning Task ===
D  Task ID: task_...
D  From: <текущая фаза>
D  To: <новая фаза>
D  Can transition: true
```

---

## Чеклист проверки

- [ ] Сценарий 1: EXECUTION → VALIDATION → DONE (обычный)
  - [ ] Шаг 1: awaitingConfirmation = true
  - [ ] Шаг 2: Переход в DONE при подтверждении

- [ ] Сценарий 2: VALIDATION → DONE (через transitionTo)
  - [ ] Появляется лог "Explicit transition requested to: DONE"
  - [ ] Прямой переход в DONE

- [ ] Сценарий 3: VALIDATION → EXECUTION (отказ)
  - [ ] Переход в EXECUTION при отрицании

- [ ] Сценарий 4: VALIDATION → NEW_TASK
  - [ ] Создаётся новая задача
  - [ ] Старая задача завершается

- [ ] Сценарий 5: PLANNING → EXECUTION
  - [ ] Переход при подтверждении

- [ ] Сценарий 6: PLANNING → EXECUTION (через transitionTo)
  - [ ] Появляется лог "Explicit transition requested to: EXECUTION"

---

## Как тестировать

### Через приложение:
1. Установить новую версию приложения
2. Запустить каждый сценарий последовательно
3. Проверить логи через Logcat

### Фильтрация логов:
```bash
adb logcat | grep "TaskIntentHandler\|TaskStateMachine\|Transitioning Task"
```

### Или в Android Studio:
1. Открой Logcat
2. Фильтр: `com.example.aiadventchallenge`
3. Ищи сообщения от `ChatViewModel` и `TaskStateMachine`
