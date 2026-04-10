# Pipeline & Scheduler Implementation

## Что реализовано

### ✅ Фаза 1: Storage & Repository Layer
- **ReminderDatabase** - единая база данных для фитнес-логов, scheduled summaries, reminders и reminder events
- **ReminderDao** - CRUD операции для reminders
- **ReminderEventDao** - CRUD операции для reminder events
- **ReminderRepository** - бизнес-логика для reminders
- **FitnessReminderRepository** - агрегированный репозиторий для фитнес-данных и reminders

### ✅ Фаза 2: Service Layer
- **ReminderService** - создание напоминаний, проверка триггеров, построение контекста
- **ReminderAnalysisService** - анализ контекста, персонализация сообщений, принятие решений о триггерах

### ✅ Фаза 3: Pipeline Infrastructure
- **PipelineStep** - интерфейс шага пайплайна
- **PipelineContext** - контекст выполнения с данными между шагами
- **PipelineResult** - типизированный результат выполнения (Success/Failure)
- **PipelineExecutor** - оркестратор для выполнения цепочек шагов

### ✅ Фаза 4: Pipeline Steps
- **LoadLogsStep** - загрузка фитнес-логов за период
- **CalculateSummaryStep** - расчет метрик и генерация summary
- **SaveSummaryStep** - сохранение summary в базу данных
- **CheckRemindersStep** - проверка due reminders
- **AnalyzeReminderStep** - анализ контекста для персонализации
- **CreateEventStep** - создание reminder event

### ✅ Фаза 5: Use Cases
- **WeeklySummaryPipeline** - pipeline для еженедельного отчета
- **DailyReminderPipeline** - pipeline для ежедневных напоминаний

### ✅ Фаза 6: Scheduler
- **DailyReminderScheduler** - ежедневные проверки reminders
- **SchedulerOrchestrator** - управление всеми планировщиками
- **BackgroundSummaryScheduler** - рефакторинг для использования pipeline

### ✅ Фаза 7: MCP Tools
- **create_reminder** - создание нового напоминания
- **check_due_reminders** - проверка due reminders
- **get_active_reminders** - список активных напоминаний
- **list_available_jobs** - список доступных фоновых задач
- **run_job_now** - запуск задачи вручную
- **get_job_status** - статус задачи

### ✅ Фаза 8: Testing
- **DemoScenario** - полный демонстрационный сценарий
- Проверка автоматического выполнения pipelines
- Проверка передачи данных между шагами
- Верификация персистентности данных

---

## Архитектурные особенности

### Pipeline-Ready Design
```
PipelineStep<I, O>
  ↓ execute(input, context)
PipelineResult<Success<O> | Failure>
  ↓
PipelineExecutor.execute(steps, input, context)
```

### Единый формат ответа инструментов
```kotlin
data class ToolResponse<T>(
    val success: Boolean,
    val tool: String,
    val timestamp: Long,
    val data: T?,
    val error: String?
)
```

### Типизированные DTO для pipeline steps
```kotlin
// Входы/выходы для каждого шага
LoadLogsInput → LoadLogsOutput
CalculateSummaryInput → CalculateSummaryOutput
SaveSummaryInput → SaveSummaryOutput
```

---

## Примеры использования

### 1. Еженедельный Summary Pipeline
```kotlin
val pipeline = WeeklySummaryPipeline(repository, summaryService)
val (result, summary) = pipeline.executeWeeklySummaryWithOutput(
    days = 7,
    toDate = LocalDate.now()
)
```

### 2. Ежедневный Reminder Pipeline
```kotlin
val pipeline = DailyReminderPipeline(reminderService, analysisService)
val result = pipeline.executeDailyReminders(
    date = LocalDate.now(),
    time = LocalTime.now()
)
```

### 3. Управление через Scheduler Orchestrator
```kotlin
val orchestrator = SchedulerOrchestrator(
    repository,
    reminderService,
    analysisService,
    summaryService
)

// Запуск всех планировщиков
orchestrator.startAll()

// Ручной запуск задачи
val summary = orchestrator.runWeeklySummaryNow()

// Статус всех задач
orchestrator.getAllStatuses()
```

### 4. MCP Tools (через JSON-RPC)
```bash
# Создание напоминания
curl -X POST http://localhost:8080 \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "create_reminder",
    "params": {
      "type": "WORKOUT",
      "title": "Тренировка!",
      "message": "Не забудь про тренировку",
      "time": "09:00",
      "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"]
    }
  }'

# Запуск задачи вручную
curl -X POST http://localhost:8080 \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "run_job_now",
    "params": {
      "jobId": "weekly_summary"
    }
  }'

# Список доступных задач
curl -X POST http://localhost:8080 \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "list_available_jobs",
    "params": {}
  }'
```

---

## Запуск демо-сценария

### Через Gradle:
```bash
./gradlew :mcp-server:run
```

### Программно:
```kotlin
import com.example.mcp.server.demo.DemoScenario
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val demo = DemoScenario()
    demo.runFullDemo()
    demo.cleanup()
}
```

---

## Будущая расширяемость

### Пример 1: Многошаговый pipeline для анализа питания
```kotlin
val nutritionPipeline = listOf<PipelineStep<*, *>>(
    LoadNutritionLogsStep(),
    AnalyzeAdherenceStep(),
    DetectDeviationsStep(),
    GenerateNutritionReportStep(),
    SaveReportStep()
)

executor.execute(nutritionPipeline, initialInput, context)
```

### Пример 2: Цепочка для предупреждений о перетренированности
```kotlin
val overtrainingPipeline = listOf<PipelineStep<*, *>>(
    LoadRecoveryDataStep(),
    CalculateRecoveryScoreStep(),
    AssessOvertrainingRiskStep(),
    GenerateAlertIfNeededStep(),
    SendNotificationStep(),
    LogAlertStep()
)

executor.execute(overtrainingPipeline, initialInput, context)
```

### Пример 3: Комбинированный pipeline
```kotlin
val combinedPipeline = listOf<PipelineStep<*, *>>(
    LoadLogsStep(),
    CalculateMetricsStep(),
    CheckRemindersStep(),
    GenerateSummaryAndRemindersStep(),
    SaveAllStep()
)

executor.execute(combinedPipeline, initialInput, context)
```

---

## Структура файлов

```
mcp-server/src/main/kotlin/com/example/mcp/server/
├── handler/
│   └── McpJsonRpcHandler.kt              # MCP tool handlers
├── model/
│   ├── fitness/
│   │   ├── FitnessLog.kt
│   │   ├── FitnessSummary.kt
│   │   └── ScheduledSummary.kt
│   ├── reminder/
│   │   ├── Reminder.kt
│   │   └── ReminderEvent.kt
│   └── JsonRpcModels.kt                 # MCP models + DTO
├── data/
│   └── fitness/
│       ├── ReminderDatabase.kt            # Unified DB
│       ├── FitnessLogDao.kt
│       ├── ScheduledSummaryDao.kt
│       ├── ReminderDao.kt
│       ├── ReminderEventDao.kt
│       ├── FitnessRepository.kt
│       ├── ReminderRepository.kt
│       └── FitnessReminderRepository.kt   # Aggregated
├── service/
│   ├── fitness/
│   │   └── FitnessSummaryService.kt
│   └── reminder/
│       ├── ReminderService.kt
│       └── ReminderAnalysisService.kt
├── pipeline/
│   ├── PipelineStep.kt
│   ├── PipelineContext.kt
│   ├── PipelineResult.kt
│   ├── PipelineExecutor.kt
│   ├── steps/
│   │   ├── LoadLogsStep.kt
│   │   ├── CalculateSummaryStep.kt
│   │   ├── SaveSummaryStep.kt
│   │   ├── CheckRemindersStep.kt
│   │   ├── AnalyzeReminderStep.kt
│   │   └── CreateEventStep.kt
│   └── usecases/
│       ├── WeeklySummaryPipeline.kt
│       └── DailyReminderPipeline.kt
├── scheduler/
│   ├── BackgroundSummaryScheduler.kt
│   ├── DailyReminderScheduler.kt
│   └── SchedulerOrchestrator.kt
├── demo/
│   └── DemoScenario.kt                 # Full demo
└── Main.kt                             # HTTP Server entry point
```

---

## Ключевые преимущества

✅ **Pipeline-Ready** - уже подготовлено к композиции инструментов
✅ **Единые контракты** - типизированные DTO для всех шагов
✅ **Отдельные слои** - tools → use cases → services → repositories
✅ **Scheduler-agnostic** - планировщики вызывают use cases, не знают про MCP
✅ **Расширяемость** - легко добавлять новые steps, pipelines, reminders
✅ **Типизация** - compile-time проверка типов между шагами
✅ **Error Handling** - graceful failure propagation через PipelineResult
✅ **Testable** - каждый шаг можно тестировать независимо

---

## Следующие шаги (для следующей задачи)

Теперь можно легко реализовать композицию MCP tools:

1. **Multi-step MCP tools**:
   - tools возвращают PipelineResult
   - client может собирать цепочки
   - автоматическая передача данных между tools

2. **Dynamic pipeline composition**:
   - LLM может решать какие tools вызывать
   - автоматически собирать pipeline на основе запроса

3. **Tool chaining**:
   - output одного tool → input следующего
   - явные DTO для contract-ов между tools

4. **Progressive enhancement**:
   - можно начать с одного tool
   - постепенно добавлять новые в цепочку

Архитектура готова! 🚀