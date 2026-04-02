# Конечный автомат для задач (Task State Machine)

## Обзор

Конечный автомат управляет состоянием задачи в фитнес-ассистенте. Позволяет отслеживать прогресс, автоматически переключать этапы и возобновлять работу с прерванных задач.

---

## Этапы задачи (TaskPhase)

```
RESEARCH → PLANNING → EXECUTION → VALIDATION → DONE
```

| Этап | Описание | Позиция |
|------|----------|---------|
| RESEARCH | Исследование задачи, анализ требований | 1 |
| PLANNING | Формирование плана действий | 2 |
| EXECUTION | Выполнение плана | 3 |
| VALIDATION | Проверка результата | 4 |
| DONE | Задача завершена | 5 |

---

## Модели

### TaskContext
```kotlin
data class TaskContext(
    val taskId: String,
    val query: String,
    val phase: TaskPhase = TaskPhase.RESEARCH,
    val currentStep: Int = 1,
    val totalSteps: Int = 1,
    val currentAction: String = "",
    val plan: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val profile: FitnessProfileType,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
```

### TaskAction
```kotlin
sealed class TaskAction {
    data class Create(val query: String, val profile: FitnessProfileType)
    data class UpdatePhase(val phase: TaskPhase)
    data class AdvanceStep(val steps: Int = 1)
    data class UpdatePlan(val plan: List<String>)
    data class AddDone(val item: String)
    data class UpdateAction(val action: String)
    data class Pause(val taskId: String)
    data object Resume
    data class Complete(val finalResult: String = "")
    data class Transition(val toPhase: TaskPhase)
}
```

---

## Использование в ChatViewModel

### Создать задачу
```kotlin
viewModel.createTask("Создать программу тренировок на неделю")
```

### Продвинуть задачу
```kotlin
viewModel.advanceTask()
```

### Обновить план
```kotlin
viewModel.updateTaskAction(TaskAction.UpdatePlan(listOf(
    "Анализ текущего уровня",
    "Выбор типа тренировок",
    "Создание расписания",
    "Проверка и корректировка"
)))
```

### Добавить выполненный шаг
```kotlin
viewModel.updateTaskAction(TaskAction.AddDone("Анализ завершён"))
```

### Завершить задачу
```kotlin
viewModel.completeTask("Программа готова")
```

### Пауза / Возобновление
```kotlin
viewModel.pauseTask()   // Задача остаётся в текущем состоянии
viewModel.resumeTask()  // Продолжение с того же места
```

---

## Промпт-билдер

### Формирование промпта
```kotlin
val prompt = TaskPromptBuilder.buildPrompt(
    query = "Создать программу тренировок",
    ctx = taskContext,
    profile = FitnessProfileType.INTERMEDIATE
)
```

### Результат
```
|[STATE]|Выполнение step 2/4|
|[CURRENT]|Выбор типа тренировок|
|[PLAN]|Анализ уровня; Выбор типа; Расписание; Проверка|
|[DONE]|Анализ уровня завершён|
|[PROFILE]|INTERMEDIATE|
|[QUERY]|Создать программу тренировок|

Rules:
- Работай только в рамках current step
- Не перепрыгивай этапы
- Если step завершён - верни next_step
```

---

## Правила переходов (State Machine)

### Разрешённые переходы
```
RESEARCH   → PLANNING, EXECUTION
PLANNING   → EXECUTION
EXECUTION  → VALIDATION
VALIDATION → EXECUTION, DONE
```

### Проверка перехода
```kotlin
val stateMachine = TaskStateMachine()
val canTransition = stateMachine.canTransition(
    from = TaskPhase.RESEARCH,
    to = TaskPhase.EXECUTION
) // true
```

---

## Хранение состояния

### Персистентность в БД
- **TaskEntity** - сохраняет состояние задачи
- **TaskDao** - CRUD операции
- **TaskRepository** - бизнес-логика
- **Migration 3 → 4** - создание таблицы `tasks`

### Загрузка при старте
```kotlin
init {
    loadActiveTask() // Автоматически загружает активную задачу
}
```

---

## UI-компоненты

### TaskProgressIndicator
Индикатор прогресса с фазой и шагами:
```kotlin
TaskProgressIndicator(
    taskContext = taskContext
)
```

### TaskPhaseBadge
Бейдж текущей фазы:
```kotlin
TaskPhaseBadge(phase = TaskPhase.EXECUTION)
```

### TaskPlanView
Отображение плана и выполненных шагов:
```kotlin
TaskPlanView(taskContext = taskContext)
```

---

## Интеграция с sendMessage()

### Автоматическое включение контекста
```kotlin
val config = agent.buildRequestConfigWithTask(
    taskContext = _taskContext.value,  // null → обычный режим
    fitnessProfile = _chatUiState.value.fitnessProfile
)
```

### Парсинг ответа AI
```kotlin
val response = TaskPromptBuilder.parseAiResponse(aiResponse)
val stepCompleted = response.stepCompleted
val nextAction = response.nextAction
```

---

## Пример workflow

### Пользователь: "Создать программу тренировок"

**1. Создание задачи**
```kotlin
taskContext = TaskContext(
    phase = RESEARCH,
    currentStep = 1,
    currentAction = "Анализ запроса"
)
```

**2. AI анализирует и создаёт план**
```json
{
  "plan": [
    "Анализ текущего уровня",
    "Выбор типа тренировок",
    "Создание расписания",
    "Проверка и корректировка"
  ]
}
```

**3. Переход на фазу EXECUTION**
```kotlin
viewModel.updateTaskAction(TaskAction.UpdatePlan(plan))
viewModel.transitionTaskTo(TaskPhase.EXECUTION)
```

**4. Выполнение шагов**
```kotlin
step 1: "Анализ уровня" → done
step 2: "Выбор типа" → current
step 3: "Расписание" → pending
```

**5. Пользователь отвлекается**
```kotlin
viewModel.pauseTask()  // Задача сохраняется
```

**6. Возобновление**
```kotlin
viewModel.resumeTask()  // Продолжение с шага 2
```

**7. Завершение**
```kotlin
viewModel.completeTask("Программа готова")
// phase = DONE, progress = 100%
```

---

## Документация по API

### TaskRepository
```kotlin
suspend fun createTask(query: String, profile: FitnessProfileType): TaskContext
suspend fun updateTask(taskId: String, action: TaskAction): TaskContext?
suspend fun getActiveTask(): TaskContext?
suspend fun getTaskById(taskId: String): TaskContext?
fun getAllTasks(): Flow<List<TaskContext>>
suspend fun setActiveTask(taskId: String)
suspend fun deactivateAllTasks()
suspend fun deleteTask(taskId: String)
```

---

## TODO

- [ ] Добавить unit тесты для TaskStateMachine
- [ ] Реализовать визуализацию переходов
- [ ] Добавить поддержку многозадачности (несколько активных задач)
- [ ] Оптимизировать хранение истории изменений задачи
- [ ] Добавить экспорт задачи в JSON
