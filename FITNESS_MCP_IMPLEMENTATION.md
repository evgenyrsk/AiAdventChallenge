# Weekly Fitness Summary - реализация MCP tools

## Обзор

Реализован планировщик и фоновые задачи для фитнес-ассистента через MCP. Система позволяет:
- Хранить фитнес-логи пользователя
- Агрегировать данные за период
- Генерировать автоматические сводки по расписанию
- Получать аналитику через MCP tools

## Архитектура

```
mcp-server/src/main/kotlin/com/example/mcp/server/
├── Main.kt                          # Точка входа сервера
├── handler/
│   └── McpJsonRpcHandler.kt        # JSON-RPC handlers с 4 новыми tools
├── model/
│   ├── JsonRpcModels.kt            # Обновлён: новые result types
│   └── fitness/                    # Модели данных
│       ├── FitnessLog.kt
│       ├── ScheduledSummary.kt
│       └── FitnessSummary.kt
├── data/
│   └── fitness/                    # Слой хранения
│       ├── FitnessDatabase.kt      # SQLite соединение
│       ├── FitnessLogDao.kt        # DAO для логов
│       └── ScheduledSummaryDao.kt  # DAO для сводок
├── service/
│   └── fitness/                    # Бизнес-логика
│       └── FitnessSummaryService.kt # Агрегация данных
└── scheduler/                      # Фоновые задачи
    └── BackgroundSummaryScheduler.kt # Планировщик
```

## MCP Tools

### 1. add_fitness_log

Добавляет фитнес-запись за дату.

**Входные параметры:**
```json
{
  "date": "2026-04-08",
  "weight": 82.4,
  "calories": 2450,
  "protein": 165,
  "workoutCompleted": true,
  "steps": 8400,
  "sleepHours": 7.2,
  "notes": "Тренировка груди и трицепса"
}
```

**Ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "addFitnessLogResult": {
      "success": true,
      "id": "fitness_log_...",
      "message": "Fitness log added successfully"
    }
  }
}
```

### 2. get_fitness_summary

Возвращает агрегированную сводку за период.

**Входные параметры:**
```json
{
  "period": "last_7_days"  // или "last_30_days", "all"
}
```

**Ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "fitnessSummaryResult": {
      "period": "last_7_days",
      "entriesCount": 6,
      "avgWeight": 82.1,
      "workoutsCompleted": 4,
      "avgSteps": 7810,
      "avgSleepHours": 7.0,
      "avgProtein": 158,
      "adherenceScore": 0.71,
      "summaryText": "За последние 7 дней выполнено 4 тренировки..."
    }
  }
}
```

### 3. run_scheduled_summary

Ручной запуск фоновой задачи.

**Входные параметры:**
Нет.

**Ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "runScheduledSummaryResult": {
      "success": true,
      "summaryId": "scheduled_summary_...",
      "message": "Summary generated successfully",
      "summary": {
        "id": "scheduled_summary_...",
        "period": "last_7_days",
        "entriesCount": 6,
        "avgWeight": 82.1,
        "workoutsCompleted": 4,
        "avgSteps": 7810,
        "avgSleepHours": 7.0,
        "avgProtein": 158,
        "adherenceScore": 0.71,
        "summaryText": "За последние 7 дней выполнено 4 тренировки...",
        "createdAt": "2026-04-08 12:00:00"
      }
    }
  }
}
```

### 4. get_latest_scheduled_summary

Возвращает последнюю автоматически сгенерированную сводку.

**Входные параметры:**
Нет.

**Ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "scheduledSummaryResult": {
      "id": "scheduled_summary_...",
      "period": "last_7_days",
      "entriesCount": 6,
      "avgWeight": 82.1,
      "workoutsCompleted": 4,
      "avgSteps": 7810,
      "avgSleepHours": 7.0,
      "avgProtein": 158,
      "adherenceScore": 0.71,
      "summaryText": "За последние 7 дней выполнено 4 тренировки...",
      "createdAt": "2026-04-08 12:00:00"
    }
  }
}
```

## Scheduler

**Demo mode (по умолчанию):**
- Интервал: 1 минута
- Удобно для тестирования

**Production mode:**
- Интервал: 24 часа
- Настройка через `intervalMinutes` в `BackgroundSummaryScheduler.kt`

**Переключение режима:**

В `McpJsonRpcHandler.kt`:
```kotlin
private val scheduler = BackgroundSummaryScheduler(
    repository = fitnessRepository,
    summaryService = fitnessSummaryService,
    intervalMinutes = 1  // Demo mode: 1, Production: 1440 (24 часа)
)
```

## Хранение данных

SQLite база: `./fitness_data.db` (в рабочей директории сервера)

> Legacy note: это описание относится к старому fitness summary flow. Для актуального document indexing demo используется другая БД: `mcp-server/output/document-index/document_index.db`.

**Таблицы:**

### fitness_logs
- id (TEXT PRIMARY KEY)
- date (TEXT NOT NULL)
- weight (REAL)
- calories (INTEGER)
- protein (INTEGER)
- workout_completed (INTEGER NOT NULL DEFAULT 0)
- steps (INTEGER)
- sleep_hours (REAL)
- notes (TEXT)
- created_at (INTEGER NOT NULL)

### scheduled_summaries
- id (TEXT PRIMARY KEY)
- period (TEXT NOT NULL)
- entries_count (INTEGER NOT NULL)
- avg_weight (REAL)
- workouts_completed (INTEGER NOT NULL DEFAULT 0)
- avg_steps (INTEGER)
- avg_sleep_hours (REAL)
- avg_protein (INTEGER)
- adherence_score (REAL NOT NULL DEFAULT 0.0)
- summary_text (TEXT NOT NULL)
- created_at (INTEGER NOT NULL)

## Запуск и тестирование

### 1. Запуск MCP-сервера

```bash
cd mcp-server
./gradlew run
```

Сервер запустится на `http://10.0.2.2:8080`

### 2. Запуск тестового сценария

В новом терминале:

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
./test_fitness_mcp.sh
```

Скрипт выполнит:
- Проверку здоровья сервера
- Получение списка доступных tools
- Добавление 7 фитнес-логов за последние дни
- Получение сводки за 7 дней
- Ручной запуск фоновой задачи
- Получение последней автоматически сгенерированной сводки

### 3. Тестирование вручную через curl

```bash
# Добавить фитнес-лог
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "add_fitness_log",
    "params": {
      "date": "2026-04-08",
      "weight": 82.4,
      "calories": 2450,
      "protein": 165,
      "workoutCompleted": true,
      "steps": 8400,
      "sleepHours": 7.2,
      "notes": "Тренировка груди"
    }
  }'

# Получить сводку за 7 дней
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "get_fitness_summary",
    "params": {
      "period": "last_7_days"
    }
  }'

# Запустить scheduled summary вручную
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "run_scheduled_summary"
  }'

# Получить последнюю scheduled summary
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "get_latest_scheduled_summary"
  }'
```

## Интеграция с Android-приложением

Для использования из Android-клиента через MCP SDK:

```kotlin
// Добавить фитнес-лог
val callMcpToolUseCase = CallMcpToolUseCase(...)
val result = callMcpToolUseCase(
    "add_fitness_log",
    mapOf(
        "date" to "2026-04-08",
        "weight" to 82.4,
        "calories" to 2450,
        "protein" to 165,
        "workoutCompleted" to true,
        "steps" to 8400,
        "sleepHours" to 7.2,
        "notes" to "Тренировка груди"
    )
)

// Получить сводку
val summary = callMcpToolUseCase(
    "get_fitness_summary",
    mapOf("period" to "last_7_days")
)

// Получить последнюю автоматическую сводку
val latestSummary = callMcpToolUseCase(
    "get_latest_scheduled_summary",
    emptyMap()
)
```

## Логика агрегации

**Расчёт метрик:**
- Средний вес за период
- Количество тренировок
- Средние шаги, сон, белок
- Adherence score (процент дней с тренировкой)

**Генерация summaryText:**
- Если тренировок < 3/7 дней → "Низкая регулярность тренировок"
- Если белок < 120г → "Недостаток белка"
- Если сон < 7 часов → "Недостаточное время сна для восстановления"
- Если шаги < 7000 → "Низкая бытовая активность"

**Пример summaryText:**
```
За последние 7 дней выполнено 4 тренировки, средний вес 82.1 кг, средний сон 7.0 ч.
Низкая бытовая активность, хороший уровень потребления белка.
```

## Обработка ошибок

- **Пустые данные:** Возвращается summary с entriesCount=0 и сообщением "Нет данных за выбранный период"
- **Некорректные даты:** Ошибка валидации параметров
- **Отсутствие summary:** Ошибка "No scheduled summary found"
- **Проблемы записи в БД:** success=false в AddFitnessLogResult

## Зависимости

Добавлено в `mcp-server/build.gradle.kts`:
```kotlin
implementation("org.xerial:sqlite-jdbc:3.45.1.0")
```

## Критерии готовности

✅ Есть хотя бы одна реально работающая фоновая задача
✅ Данные сохраняются между перезапусками (SQLite)
✅ Summary считается автоматически по расписанию (1 минута в demo mode)
✅ Агрегированный результат можно получить через MCP tool
✅ Код структурирован и читаем (разделён на слои)
✅ Можно локально поднять сервер и проверить работу

## Демонстрация

После запуска тестового сценария вы увидите:

1. Добавление 7 фитнес-логов
2. Получение агрегированной сводки
3. Запуск scheduled summary вручную
4. Получение последней автоматически сгенерированной сводки

В консоли сервера вы увидите автоматическое запуск scheduler каждые 1 минуту:
```
🔄 Running scheduled summary manually
✅ Summary generated and saved: 7 entries
   Summary: За последние 7 дней выполнено 5 тренировок, средний вес 82.9 кг...
```

## Следующие улучшения

1. Добавить webhook для отправки summary в Android-приложение
2. Добавить настройку расписания через параметры MCP tool
3. Добавить экспорт данных в CSV/JSON
4. Добавить детализацию по типам тренировок
5. Добавить графики прогресса
