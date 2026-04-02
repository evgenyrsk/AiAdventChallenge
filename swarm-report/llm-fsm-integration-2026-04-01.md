# Отчет: Интеграция LLM с конечным автоматом задач

## Дата
2026-04-01

## Краткое описание задачи
Доработать ChatViewModel так, чтобы LLM в процессе общения учитывала правила конечного автомата задач. Каждый этап должен сопровождаться наличием в сообщении идентификаторов, позволяющих изменять статусы текущей задачи.

---

## Итоги Research

### Текущая реализация
- ✅ Конечный автомат с 5 фазами: RESEARCH → PLANNING → EXECUTION → VALIDATION → DONE
- ✅ Валидные переходы определены в `TaskStateMachine`
- ✅ Базовый парсинг ответов AI (step_completed, transition_to)
- ✅ Режим работы с задачей через `processWithTask()`

### Выявленные проблемы
- ❌ LLM не умеет определять: новая задача или продолжение текущей
- ❌ Промпты слишком общие, нет фазо-специфичных инструкций
- ❌ Нет явного механизма прерывания задачи и создания новой
- ❌ Метаданные в ответах AI ограничены
- ❌ Логика переключения режимов жестко привязана к `isActive`

---

## План реализации

### Этап 1: Расширение моделей данных
✅ **Выполнено**

Добавлены новые типы в `TaskPromptBuilder.kt`:
- `enum class TaskIntent` - для классификации намерений пользователя:
  - NEW_TASK - новая задача
  - CONTINUE_TASK - продолжение текущей задачи
  - SWITCH_TASK - переключение на новую задачу
  - PAUSE_TASK - приостановка задачи
  - CLARIFICATION - требуется уточнение

- `data class EnhancedTaskAiResponse` - расширенный ответ AI с полями:
  - `taskIntent: TaskIntent`
  - `stepCompleted: Boolean`
  - `nextAction: String`
  - `result: String`
  - `transitionTo: TaskPhase?`
  - `newTaskQuery: String?`
  - `pauseTask: Boolean`
  - `needClarification: String?`
  - `errorMessage: String?`

---

### Этап 2: Фазо-специфичные промпты
✅ **Выполнено**

Добавлен метод `buildPhaseSpecificPrompt()` в `TaskPromptBuilder.kt`:

**Структура промпта:**
1. Текущее состояние задачи (этап, шаг, прогресс)
2. Допустимые переходы из `TaskStateMachine`
3. Инструкции для текущей фазы:
   - RESEARCH: сбор информации о задаче
   - PLANNING: формирование плана
   - EXECUTION: выполнение по шагам
   - VALIDATION: проверка результата
   - DONE: завершение

4. Правила классификации запросов с примерами
5. Формат ответа AI с метаданными

---

### Этап 3: Логика классификации запросов
✅ **Выполнено**

В `ChatViewModel.kt` добавлено:

1. **Метод `processIntelligentMessage()`** - умная обработка сообщений:
   - Отправляет запрос LLM с фазо-специфичным промптом
   - Парсит `EnhancedTaskAiResponse`
   - Обрабатывает все намерения пользователя

2. **Метод `handleTaskIntent()`** - обработка намерений:
   ```kotlin
   NEW_TASK → createTask()
   SWITCH_TASK → pauseTask() + createTask()
   PAUSE_TASK → pauseTask()
   CONTINUE_TASK/CLARIFICATION → advanceTask() + transitionTaskTo()
   ```

3. **Обновлен метод `sendMessage()`**:
   ```kotlin
   if (activeTask?.isActive == true && activeTask.phase != DONE) {
       processIntelligentMessage(userInput)
   } else {
       processNormalMessage(userInput)
   }
   ```

---

### Этап 4: Обновленный парсинг ответов
✅ **Выполнено**

В `TaskPromptBuilder.kt`:

1. **Метод `parseEnhancedAiResponse()`** - парсинг всех полей из ответа AI:
   - Извлекает `taskIntent` из метаданных
   - Извлекает `step_completed`, `next_action`, `transition_to`
   - Извлекает `new_task_query` для новых задач
   - Извлекает `need_clarification` для уточнений
   - Извлекает текст ответа (перед метаданными)

2. **Метод `extractTaskIntent()`** - парсинг enum TaskIntent

3. **Метод `extractResult()`** - извлечение текста ответа пользователя

---

### Этап 5: Интеграция в ChatViewModel
✅ **Выполнено**

1. **Обновлен `processWithTask()`**:
   - Использует фазо-специфичный промпт
   - Парсит `EnhancedTaskAiResponse`
   - Вызывает `handleTaskIntent()` для обработки

2. **Обновлен `handleCommand()`**:
   - Добавлена проверка на пустой контент для команды `/task`

3. **Добавлены импорты**:
   - `TaskIntent`
   - `EnhancedTaskAiResponse`
   - `TaskPromptBuilder`

---

### Этап 6: Обновление ChatAgent
✅ **Выполнено**

В `ChatAgent.kt` обновлен метод `buildRequestConfigWithTask()`:

- Удален устаревший метод `buildPromptWithProfile()`
- Добавлен вызов `buildSystemPrompt()` с фазо-специфичными инструкциями
- Добавлен параметр `userInput` для передачи контекста

---

## Что реализовано (файлы, модули)

### Измененные файлы

1. **`data/config/TaskPromptBuilder.kt`** (260+ строк):
   - Добавлен `enum class TaskIntent`
   - Добавлен `data class EnhancedTaskAiResponse`
   - Добавлен метод `buildPhaseSpecificPrompt()`
   - Добавлен метод `getPhaseInstructions()`
   - Добавлен метод `buildTaskRulesPrompt()`
   - Добавлен метод `parseEnhancedAiResponse()`
   - Добавлен метод `extractTaskIntent()`
   - Добавлен метод `extractResult()`
   - Обновлен метод `buildSystemPrompt()`

2. **`ui/screens/chat/ChatViewModel.kt`** (900+ строк):
   - Добавлен метод `processIntelligentMessage()`
   - Добавлен метод `handleTaskIntent()`
   - Обновлен метод `sendMessage()`
   - Обновлен метод `processWithTask()`
   - Обновлен метод `handleCommand()`
   - Добавлены импорты новых типов

3. **`data/agent/ChatAgent.kt`** (100+ строк):
   - Обновлен метод `buildRequestConfigWithTask()`

---

## Примеры флоу

### Сценарий 1: Новая задача
```
Пользователь: "Составь программу тренировок"
↓
AI определяет: task_intent: NEW_TASK
↓
ChatViewModel: createTask("Составь программу тренировок")
↓
Статус: RESEARCH → (сбор информации) → PLANNING → EXECUTION → VALIDATION → DONE
```

### Сценарий 2: Переключение задачи
```
Пользователь: "А теперь составь протокол питания"
↓
AI определяет: task_intent: SWITCH_TASK, new_task_query: "Составить протокол питания"
↓
ChatViewModel: pauseTask() → createTask("Составить протокол питания")
↓
Создается новая задача, старая приостанавливается
```

### Сценарий 3: Продолжение задачи
```
Пользователь: "Я новичок без опыта"
↓
AI определяет: task_intent: CONTINUE_TASK, step_completed: true, transition_to: PLANNING
↓
ChatViewModel: advanceTask() → transitionTaskTo(PLANNING)
↓
Задача переходит на следующий этап
```

### Сценарий 4: Приостановка задачи
```
Пользователь: "Пауза"
↓
AI определяет: task_intent: PAUSE_TASK
↓
ChatViewModel: pauseTask()
↓
Задача приостанавливается, но не удаляется
```

---

## Результаты Validation

✅ **Компиляция**: Успешно
```
./gradlew compileDebugKotlin
BUILD SUCCESSFUL
```

✅ **Проверка типов**: Все типы корректно импортированы и использованы
- `TaskIntent` - enum для классификации намерений
- `EnhancedTaskAiResponse` - data class для расширенного ответа AI
- `TaskPromptBuilder` - методы для построения промптов и парсинга

✅ **Логика переходов**: Использует `TaskStateMachine` для валидации

---

## Проблемы и откаты

**Нет**. Все изменения прошли успешно без откатов.

---

## Статус
**Done** ✅

Все запланированные изменения успешно реализованы:
- Расширены модели данных для поддержки классификации намерений
- Созданы фазо-специфичные промпты с правилами конечного автомата
- Реализована умная обработка сообщений с автоматическим определением намерений
- Обновлен парсинг ответов AI для извлечения всех необходимых метаданных
- Интегрирована новая логика в ChatViewModel и ChatAgent
- Код успешно компилируется без ошибок
