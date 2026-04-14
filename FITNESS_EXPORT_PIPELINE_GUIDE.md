# Руководство по тестированию: Fitness Summary Export Pipeline

## Обзор

Это руководство описывает как протестировать новую функциональность **композиции MCP-инструментов** для экспорта фитнес-сводок в файлы.

### Новые MCP инструменты

| Инструмент | Описание |
|-----------|-----------|
| `search_fitness_logs` | Поиск фитнес-логов за период (last_7_days, last_30_days) |
| `summarize_fitness_logs` | Агрегация логов и генерация summary с метриками |
| `save_summary_to_file` | Сохранение summary в JSON или TXT файл |
| `run_fitness_summary_export_pipeline` | Полный pipeline: search → summarize → save |

### Новый Scheduler Job

- **Job ID**: `fitness_summary_export`
- **Интервал**: раз в сутки (1440 минут)
- **Формат**: JSON по умолчанию

---

## Шаг 1: Запуск MCP сервера

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
./gradlew :mcp-server:run
```

Сервер запустится и будет слушать соединения. Вы увидите:
```
🚀 Starting all schedulers...
🚀 Fitness Summary Export Scheduler started (interval: 1440min)
✅ All schedulers started
MCP Server started on port 8080
```

---

## Шаг 2: Добавление тестовых данных

Перед тестированием нужно добавить фитнес-логи:

### Через add_fitness_log MCP tool

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "add_fitness_log",
  "params": {
    "date": "2026-04-03",
    "weight": 82.5,
    "calories": 2450,
    "protein": 160,
    "workoutCompleted": true,
    "steps": 8200,
    "sleepHours": 7.5,
    "notes": "Тренировка ног"
  }
}
```

### Через DemoFitnessSummaryExport (рекомендуется)

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
./gradlew :mcp-server:runDemo
```

Демо автоматически добавит 7 тестовых записей за последнюю неделю.

---

## Шаг 3: Тестирование индивидуальных MCP tools

### 3.1. Тестирование search_fitness_logs

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "search_fitness_logs",
  "params": {
    "period": "last_7_days",
    "days": 7
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "message": "Found 7 fitness logs for period last_7_days",
    "toolResult": {
      "success": true,
      "tool": "search_fitness_logs",
      "timestamp": 1744248000000,
      "data": {
        "period": "last_7_days",
        "entries": [
          {
            "date": "2026-04-03",
            "weight": 82.5,
            "calories": 2450,
            "protein": 160,
            "workoutCompleted": true,
            "steps": 8200,
            "sleepHours": 7.5,
            "notes": "Тренировка ног"
          },
          ...
        ],
        "startDate": "2026-04-03",
        "endDate": "2026-04-10"
      }
    }
  },
  "error": null
}
```

### 3.2. Тестирование summarize_fitness_logs

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "summarize_fitness_logs",
  "params": {
    "period": "last_7_days",
    "entries": [
      {
        "date": "2026-04-03",
        "weight": 82.5,
        "calories": 2450,
        "protein": 160,
        "workoutCompleted": true,
        "steps": 8200,
        "sleepHours": 7.5,
        "notes": "Тренировка ног"
      },
      {
        "date": "2026-04-04",
        "weight": 82.3,
        "calories": 2600,
        "protein": 175,
        "workoutCompleted": false,
        "steps": 6500,
        "sleepHours": 6.8,
        "notes": "День отдыха"
      }
    ]
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "message": "Generated fitness summary for last_7_days with 2 entries",
    "toolResult": {
      "success": true,
      "tool": "summarize_fitness_logs",
      "timestamp": 1744248000000,
      "data": {
        "period": "last_7_days",
        "entriesCount": 2,
        "avgWeight": 82.4,
        "workoutsCompleted": 1,
        "avgSteps": 7350,
        "avgSleepHours": 7.15,
        "avgProtein": 167,
        "summaryText": "За период выполнено 1 тренировок, средний вес 82.4 кг, средний сон 7.2 ч. Низкая регулярность тренировок (1 из 2 дней)."
      }
    }
  },
  "error": null
}
```

### 3.3. Тестирование save_summary_to_file (JSON)

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "save_summary_to_file",
  "params": {
    "period": "last_7_days",
    "entriesCount": 7,
    "avgWeight": 82.1,
    "workoutsCompleted": 5,
    "avgSteps": 8100,
    "avgSleepHours": 7.2,
    "avgProtein": 165,
    "summaryText": "За период выполнено 5 тренировок, средний вес 82.1 кг, средний сон 7.2 ч. Хорошая регулярность тренировок, Хороший уровень потребления белка.",
    "format": "json"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "message": "Summary saved to file: /tmp/fitness-summary-last-7-days-2026-04-10.json",
    "toolResult": {
      "success": true,
      "tool": "save_summary_to_file",
      "timestamp": 1744248000000,
      "data": {
        "filePath": "/tmp/fitness-summary-last-7-days-2026-04-10.json",
        "format": "json",
        "sizeBytes": 2048,
        "savedAt": "2026-04-10T12:00:00"
      }
    }
  },
  "error": null
}
```

### 3.4. Тестирование save_summary_to_file (TXT)

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "save_summary_to_file",
  "params": {
    "period": "last_7_days",
    "entriesCount": 7,
    "avgWeight": 82.1,
    "workoutsCompleted": 5,
    "avgSteps": 8100,
    "avgSleepHours": 7.2,
    "avgProtein": 165,
    "summaryText": "За период выполнено 5 тренировок, средний вес 82.1 кг, средний сон 7.2 ч. Хорошая регулярность тренировок.",
    "format": "txt"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "message": "Summary saved to file: /tmp/fitness-summary-last-7-days-2026-04-10.txt",
    "toolResult": {
      "success": true,
      "tool": "save_summary_to_file",
      "timestamp": 1744248000000,
      "data": {
        "filePath": "/tmp/fitness-summary-last-7-days-2026-04-10.txt",
        "format": "txt",
        "sizeBytes": 512,
        "savedAt": "2026-04-10T12:00:00"
      }
    }
  },
  "error": null
}
```

---

## Шаг 4: Тестирование полного pipeline

### 4.1. Запуск полного pipeline (JSON формат)

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "method": "run_fitness_summary_export_pipeline",
  "params": {
    "period": "last_7_days",
    "days": 7,
    "format": "json"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 5,
  "result": {
    "message": "Fitness summary exported successfully to /tmp/fitness-summary-last-7-days-2026-04-10.json",
    "toolResult": {
      "success": true,
      "tool": "run_fitness_summary_export_pipeline",
      "timestamp": 1744248000000,
      "data": {
        "success": true,
        "filePath": "/tmp/fitness-summary-last-7-days-2026-04-10.json",
        "format": "json",
        "savedAt": 1744248000000,
        "search": {
          "period": "last_7_days",
          "entriesCount": 7,
          "startDate": "2026-04-03",
          "endDate": "2026-04-10"
        },
        "summary": {
          "period": "last_7_days",
          "entriesCount": 7,
          "avgWeight": 82.1,
          "workoutsCompleted": 5,
          "avgSteps": 8100,
          "avgSleepHours": 7.2,
          "avgProtein": 165,
          "summaryText": "За период выполнено 5 тренировок, средний вес 82.1 кг, средний сон 7.2 ч. Хорошая регулярность тренировок."
        }
      }
    }
  },
  "error": null
}
```

### 4.2. Запуск полного pipeline (TXT формат)

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 6,
  "method": "run_fitness_summary_export_pipeline",
  "params": {
    "period": "last_30_days",
    "days": 30,
    "format": "txt"
  }
}
```

---

## Шаг 5: Проверка созданных файлов

### 5.1. Просмотр списка созданных файлов

```bash
ls -lh /tmp/fitness-summary-*.json
ls -lh /tmp/fitness-summary-*.txt
```

### 5.2. Просмотр содержимого JSON файла

```bash
cat /tmp/fitness-summary-last-7-days-2026-04-10.json
```

**Ожидаемое содержимое:**
```json
{
  "period": "last_7_days",
  "entriesCount": 7,
  "avgWeight": 82.1,
  "workoutsCompleted": 5,
  "avgSteps": 8100,
  "avgSleepHours": 7.2,
  "avgProtein": 165,
  "summaryText": "За период выполнено 5 тренировок, средний вес 82.1 кг, средний сон 7.2 ч. Хорошая регулярность тренировок, Хороший уровень потребления белка.",
  "exportedAt": "2026-04-10T12:00:00"
}
```

### 5.3. Просмотр содержимого TXT файла

```bash
cat /tmp/fitness-summary-last-7-days-2026-04-10.txt
```

**Ожидаемое содержимое:**
```
FITNESS SUMMARY REPORT
==================================================

Generated: 2026-04-10 12:00:00
Period: last_7_days
Entries: 7
Workouts: 5
Avg Weight: 82.1 kg
Avg Steps: 8,100
Avg Sleep: 7.2 h
Avg Protein: 165 g

--------------------------------------------------

За период выполнено 5 тренировок, средний вес 82.1 кг, средний сон 7.2 ч. Хорошая регулярность тренировок, Хороший уровень потребления белка.

--------------------------------------------------
```

---

## Шаг 6: Тестирование Scheduler

### 6.1. Проверка списка доступных jobs

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "method": "list_available_jobs",
  "params": {}
}
```

**Ожидаемый ответ (включая новый job):**
```json
{
  "jsonrpc": "2.0",
  "id": 7,
  "result": {
    "listJobsResult": {
      "jobs": [
        {
          "job_id": "daily_reminders",
          "name": "Daily Reminders",
          "description": "Check and send daily workout/hydration/protein/sleep reminders",
          "interval_minutes": 60,
          "status": "running"
        },
        {
          "job_id": "weekly_summary",
          "name": "Weekly Summary",
          "description": "Generate weekly fitness summary with metrics and insights",
          "interval_minutes": 1440,
          "status": "running"
        },
        {
          "job_id": "fitness_summary_export",
          "name": "Fitness Summary Export",
          "description": "Daily fitness summary export to file (search → summarize → save)",
          "interval_minutes": 1440,
          "status": "running"
        }
      ]
    }
  },
  "error": null
}
```

### 6.2. Проверка статуса конкретного job

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 8,
  "method": "get_job_status",
  "params": {
    "jobId": "fitness_summary_export"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 8,
  "result": {
    "getJobStatusResult": {
      "jobId": "fitness_summary_export",
      "status": "running",
      "intervalMinutes": 1440,
      "description": "Daily fitness summary export to file"
    }
  },
  "error": null
}
```

### 6.3. Ручной запуск job

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 9,
  "method": "run_job_now",
  "params": {
    "jobId": "fitness_summary_export"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 9,
  "result": {
    "runJobNowResult": {
      "success": true,
      "jobId": "fitness_summary_export",
      "resultSummary": "Fitness summary exported to /tmp/fitness-summary-last-7-days-2026-04-10.json",
      "message": "Fitness summary exported successfully"
    }
  },
  "error": null
}
```

---

## Шаг 7: Тестирование обработки ошибок

### 7.1. Ошибка: Нет фитнес-логов

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 10,
  "method": "run_fitness_summary_export_pipeline",
  "params": {
    "period": "last_7_days",
    "days": 7,
    "format": "json"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 10,
  "result": {
    "message": "No fitness logs found for period last_7_days"
  },
  "error": {
    "code": -32603,
    "message": "No fitness logs found for period last_7_days"
  }
}
```

### 7.2. Ошибка: Неверный параметр

**Запрос:**
```json
{
  "jsonrpc": "2.0",
  "id": 11,
  "method": "search_fitness_logs",
  "params": {
    "period": "invalid_period"
  }
}
```

**Ожидаемый ответ:**
```json
{
  "jsonrpc": "2.0",
  "id": 11,
  "result": null,
  "error": {
    "code": -32602,
    "message": "Invalid params: Period should be 'last_7_days' or 'last_30_days'"
  }
}
```

---

## Шаг 8: Демонстрация композиции

### 8.1. Полный сценарий с DemoFitnessSummaryExport

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
./gradlew :mcp-server:runDemo
```

**Ожидаемый вывод:**
```
============================================================
DEMO: Fitness Summary Export Pipeline
============================================================

📝 Step 1: Setup test data
📊 Adding test fitness logs for last 7 days...
   ✅ Added log for 2026-04-03: 82.5kg, workout, 8200 steps
   ✅ Added log for 2026-04-04: 82.3kg, rest, 6500 steps
   ✅ Added log for 2026-04-05: 82.1kg, workout, 8800 steps
   ✅ Added log for 2026-04-06: 82.0kg, workout, 9100 steps
   ✅ Added log for 2026-04-07: 81.8kg, rest, 7200 steps
   ✅ Added log for 2026-04-08: 81.9kg, workout, 8500 steps
   ✅ Added log for 2026-04-09: 81.7kg, workout, 8900 steps
   📊 Total logs added: 7

📝 Step 2: Run individual tools
🔧 Step 2.1: Running search_fitness_logs
   ✅ Search successful: 7 entries found
      - 2026-04-03: 82.5kg, workout
      - 2026-04-04: 82.3kg, rest

🔧 Step 2.2: Running summarize_fitness_logs
   ✅ Summary successful:
      - Entries: 7
      - Workouts: 5
      - Avg weight: 82.0kg
      - Avg steps: 8100
      - Summary: За период выполнено 5 тренировок...

🔧 Step 2.3: Running save_summary_to_file (JSON)
   ✅ Save successful:
      - File: /tmp/fitness-summary-last-7-days-2026-04-10.json
      - Format: json

📝 Step 3: Running full pipeline (search → summarize → save)
🚀 Starting pipeline: Fitness Summary Export (fitness_summary_export_xxx)
   ⏭️  Executing step: search_fitness_logs
      Description: Search fitness logs for a specified period
      ✅ Step completed successfully
   ⏭️  Executing step: summarize_fitness_logs
      Description: Aggregate fitness logs and generate summary
      ✅ Step completed successfully
   ⏭️  Executing step: save_summary_to_file
      Description: Save summary to file
      ✅ Step completed successfully
   ✅ Pipeline completed successfully
   ✅ Pipeline executed successfully!
      - File path: /tmp/fitness-summary-last-7-days-2026-04-10.json
      - Format: json
      - Saved at: 1744248000000

📝 Step 4: Verifying output files
   ✅ Found 3 recent export files:
      - fitness-summary-last-7-days-2026-04-10.json (2 KB)
      - fitness-summary-last-7-days-2026-04-09.json (1 KB)
      - fitness-summary-last-7-days-2026-04-08.json (1 KB)

   📄 Contents of latest file:
   {
     "period": "last_7_days",
     "entriesCount": 7,
     "avgWeight": 82.0,
     "workoutsCompleted": 5,
     "avgSteps": 8100,
     "avgSleepHours": 7.2,
     "avgProtein": 165,
     "summaryText": "За период выполнено 5 тренировок...",
     "exportedAt": "2026-04-10T12:00:00"
   }

============================================================
✅ Demo completed successfully!
============================================================
```

---

## Шаг 9: Дополнительные тесты

### 9.1. Тестирование разных периодов

**last_30_days:**
```json
{
  "jsonrpc": "2.0",
  "id": 12,
  "method": "run_fitness_summary_export_pipeline",
  "params": {
    "period": "last_30_days",
    "days": 30,
    "format": "json"
  }
}
```

### 9.2. Тестирование разных форматов

**TXT формат:**
```json
{
  "jsonrpc": "2.0",
  "id": 13,
  "method": "run_fitness_summary_export_pipeline",
  "params": {
    "period": "last_7_days",
    "days": 7,
    "format": "txt"
  }
}
```

### 9.3. Тестирование передачи данных между шагами

Воспользуйтесь `executeWithFullOutput()` для получения данных всех шагов:

```json
{
  "jsonrpc": "2.0",
  "id": 14,
  "method": "run_fitness_summary_export_pipeline",
  "params": {
    "period": "last_7_days",
    "days": 7,
    "format": "json"
  }
}
```

Ответ будет содержать поля:
- `search`: результаты поиска
- `summary`: агрегированные метрики
- `filePath`: путь к созданному файлу

---

## Шаг 10: Очистка тестовых данных

### Удаление созданных файлов

```bash
rm /tmp/fitness-summary-*.json
rm /tmp/fitness-summary-*.txt
```

### Очистка базы данных

```bash
rm /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/fitness_data.db
```

> Legacy note: этот pipeline guide относится к старому fitness export flow. Он не описывает storage для document indexing demo.

---

## Архитектурные проверки

### ✅ Проверка 1: Компонуемость

Каждый инструмент работает отдельно:
- ✅ `search_fitness_logs` может быть вызван один
- ✅ `summarize_fitness_logs` принимает результат `search_fitness_logs`
- ✅ `save_summary_to_file` принимает результат `summarize_fitness_logs`
- ✅ `run_fitness_summary_export_pipeline` комбинирует все три

### ✅ Проверка 2: Типизированная передача данных

- ✅ DTO определены для каждого шага
- ✅ Передача через `PipelineContext`
- ✅ Ошибка типизации вызывает compile-time ошибку

### ✅ Проверка 3: Обработка ошибок

- ✅ Ошибка любого шага останавливает pipeline
- ✅ Ошибка возвращается с описанием
- ✅ Success содержит все данные выполнения

### ✅ Проверка 4: Расширяемость

- ✅ Можно добавить новый pipeline без изменений в существующий код
- ✅ Можно добавить новый шаг в существующий pipeline
- ✅ Scheduler поддерживает новые jobs

---

## Полезные команды

### Мониторинг файлов

```bash
# Следить за появлением новых файлов
watch -n 5 'ls -lh /tmp/fitness-summary-*.json'

# Показывать содержимое последнего файла
tail -f /tmp/fitness-summary-last-7-days-$(date +%Y-%m-%d).json
```

### Проверка логов сервера

```bash
# Если сервер запущен с логированием
tail -f mcp-server.log
```

### Проверка базы данных

```bash
# Если используется SQLite
sqlite3 /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/fitness_data.db \
  "SELECT date, weight, workout_completed FROM fitness_logs ORDER BY date DESC LIMIT 10;"
```

> Legacy note: для актуального document indexing demo смотреть нужно `mcp-server/output/document-index/document_index.db`, а не `fitness_data.db`.

---

## Частые проблемы и решения

### Проблема: "No fitness logs found"

**Причина:** В базе данных нет фитнес-логов за указанный период

**Решение:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "add_fitness_log",
  "params": {
    "date": "2026-04-10",
    "weight": 82.0,
    "calories": 2500,
    "protein": 165,
    "workoutCompleted": true,
    "steps": 8000,
    "sleepHours": 7.5
  }
}
```

### Проблема: "Failed to save file"

**Причина:** Нет прав на запись в `/tmp`

**Решение:**
```bash
chmod 777 /tmp
```

Или изменить директорию для экспорта в `SummaryFileExportService`.

### Проблема: "Job not found"

**Причина:** Неверный jobId

**Решение:**
Используйте `list_available_jobs` для получения списка доступных job IDs.

---

## Заключение

После прохождения всех шагов вы сможете:
- ✅ Вызывать каждый MCP tool отдельно
- ✅ Запускать полный pipeline одной командой
- ✅ Получать результаты в JSON или TXT формате
- ✅ Проверять корректность передачи данных между шагами
- ✅ Настраивать автоматический запуск через scheduler

Архитектура готова для добавления новых pipeline и расширения функциональности!
