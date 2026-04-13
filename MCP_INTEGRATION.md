# MCP Интеграция - Фитнес Сценарий

## Архитектура

### MCP Серверы

В проекте реализована архитектура с 3 MCP серверами для фитнес-сценария:

#### 1. Fitness MCP Server (порт 8081)
- **ID:** `fitness-server-1`
- **Инструменты:**
  - `ping` - проверка соединения
  - `get_app_info` - информация о приложении
  - `calculate_nutrition_plan` - расчёт плана питания
  - `add_fitness_log` - добавление фитнес-лога
  - `get_fitness_summary` - получение сводки
  - `run_scheduled_summary` - запуск запланированной сводки
  - `get_latest_scheduled_summary` - последняя запланированная сводка
  - `search_fitness_logs` - поиск фитнес-логов
  - `summarize_fitness_logs` - суммирование фитнес-логов
  - `save_summary_to_file` - сохранение сводки в файл
  - `run_fitness_summary_export_pipeline` - запуск пайплайна экспорта

#### 2. Reminder MCP Server (порт 8082)
- **ID:** `reminder-server-1`
- **Инструменты:**
  - `create_reminder` - создание напоминания
  - `check_due_reminders` - проверка просроченных напоминаний
  - `get_active_reminders` - получение активных напоминаний
  - `list_available_jobs` - список доступных задач
  - `run_job_now` - запуск задачи сейчас
  - `get_job_status` - статус задачи
  - `create_reminder_from_summary` - создание напоминания из сводки

#### 3. Main MCP Server (порт 8080)
- **Main сервер** для оркестрации и координации
- Содержит `execute_multi_server_flow` для выполнения сценариев между серверами

## Android Интеграция

### Подключение

**URL MCP сервера:** `http://10.0.2.2:8080` (для Android Emulator)

**Компоненты:**
- `McpJsonRpcClient` - JSON-RPC клиент для общения с MCP сервером
- `McpRepository` - репозиторий для MCP операций
- `McpToolOrchestrator` - оркестратор MCP инструментов
- `CallMcpToolUseCase` - use case для вызова MCP инструментов

### Настройка

В `AppDependencies.kt`:
```kotlin
private val mcpJsonRpcClient: McpJsonRpcClient by lazy {
    McpJsonRpcClient(
        serverUrl = "http://10.0.2.2:8080"
    )
}

val mcpRepository: McpRepository by lazy {
    McpRepository(
        client = mcpJsonRpcClient
    )
}
```

## Фитнес Сценарий

### Описание

Сценарий: "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"

### Шаги выполнения

1. **Поиск фитнес-логов** (`search_fitness_logs`)
   - Сервер: Fitness (8081)
   - Получает логи за указанный период

2. **Суммирование логов** (`summarize_fitness_logs`)
   - Сервер: Fitness (8081)
   - Создаёт краткое описание из логов

3. **Создание напоминания** (`create_reminder_from_summary`)
   - Сервер: Reminder (8082)
   - Создаёт напоминание на основе сводки

### Multi-Server Flow

Используется инструмент `execute_multi_server_flow`:
```kotlin
val prompt = "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"
val result = mcpRepository.executeMultiServerFlow(prompt)
```

### Результат выполнения

```
Multi-Server Flow: Fitness Summary to Reminder
Статус: ✅ Успешно
Шагов выполнено: 3/3
Длительность: 1234ms

Шаги выполнения:
✅ fitness-server-1 → search_fitness_logs (200ms)
✅ fitness-server-1 → summarize_fitness_logs (300ms)
✅ reminder-server-1 → create_reminder_from_summary (150ms)
```

## Тестирование

### Запуск MCP сервера

```bash
cd mcp-server
gradle run
```

Сервер будет доступен на `http://localhost:8080`

Для Android Emulator используется `http://10.0.2.2:8080`

### Тестирование в Android приложении

1. Убедитесь что MCP сервер запущен
2. Запустите Android приложение в эмуляторе
3. Откройте чат и отправьте запрос для фитнес-сценария

### Примеры запросов

```
"Найди последние фитнес логи за неделю, составь сводку и создай напоминание"
"Покажи мою сводку за месяц и создай напоминание на завтра"
"Сделай экспорт фитнес данных и напомни мне про тренировку"
```

## Мониторинг и логирование

### Логи MCP клиента

Тег: `McpRepository`

```
🔗 Connecting to MCP server...
✅ Initialized: Initialized
📦 Received 11 tools
   - ping: Simple ping tool to test MCP connection
   - get_app_info: Returns information about application
   - ...
🔧 Calling MCP tool: calculate_nutrition_plan
   Params: {sex=male, age=30, ...}
✅ Tool result: StringResult
```

### Логи MCP оркестратора

Тег: `McpToolOrchestrator`

```
🔍 Checking for MCP tool in LLM response...
🔧 Calling MCP tool: search_fitness_logs
   Params: {days=7}
✅ Tool result: FitnessSummary
```

## Troubleshooting

### MCP сервер недоступен

**Ошибка:** `Connection failed: Connection refused`

**Решение:**
- Проверьте что MCP сервер запущен
- Проверьте что используется правильный URL (`http://10.0.2.2:8080` для эмулятора)
- Проверьте firewall правила

### Инструмент не найден

**Ошибка:** `Tool call failed: Tool not found`

**Решение:**
- Убедитесь что нужный инструмент зарегистрирован в MCP сервере
- Проверьте `McpServerRegistry` для списка доступных инструментов

### Multi-Server Flow не выполняется

**Ошибка:** `Multi-server flow failed`

**Решение:**
- Проверьте что все серверы (Fitness 8081, Reminder 8082) запущены
- Проверьте логи оркестратора на детальные сообщения об ошибках
- Убедитесь что инструменты `search_fitness_logs`, `summarize_fitness_logs`, `create_reminder_from_summary` доступны

## Дополнительные ресурсы

- `McpJsonRpcHandler.kt` - обработчик JSON-RPC запросов
- `MultiServerOrchestrator.kt` - оркестратор multi-server сценариев
- `FitnessFlowDebug.kt` - отладочный скрипт для фитнес-сценария
- `McpServerRegistry.kt` - регистр MCP серверов
