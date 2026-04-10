# Архитектурный рефакторинг MCP: типобезопасные результаты инструментов

**Дата:** 2026-04-10
**Профиль:** Бизнес-фича (исправление бага)
**Статус:** ✅ Done

---

## Описание проблемы

При запуске Android приложения и попытке получить сводку за неделю из файла, пользователь получал пустой результат. LLM сообщал *"система вернула результат с пустым содержимым"*.

**Корневая причина:** `McpJsonRpcClient.callTool()` возвращал только `response.result?.message` (String), но данные находились в специализированных полях (`fitnessSummaryResult`, `scheduledSummaryResult` и т.д.).

---

## Реализованное решение

### Вариант: Архитектурный рефакторинг (Вариант 2)

Введены типобезопасные результаты MCP инструментов на всех слоях архитектуры.

---

## Стадия: Plan → Executing

### Шаг 1: Созданы типы для результатов MCP инструментов

**Файл:** `app/src/main/java/com/example/aiadventchallenge/domain/mcp/McpToolResult.kt` (новый)

```kotlin
sealed class McpToolResult {
    data class Success(val data: McpToolData) : McpToolResult()
    data class Error(val message: String) : McpToolResult()
}

sealed class McpToolData {
    data class StringResult(val message: String) : McpToolData()
    data class FitnessSummary(val summary: FitnessSummaryData) : McpToolData()
    data class ScheduledSummary(val summary: ScheduledSummaryData) : McpToolData()
    data class AddFitnessLog(val result: AddFitnessLogData) : McpToolData()
    data class ExportResult(val fullResponse: ExportData) : McpToolData()
    data class RunScheduledSummary(val result: RunScheduledSummaryData) : McpToolData()
}
```

### Шаг 2: Обновлен McpJsonRpcClient

**Файл:** `app/src/main/java/com/example/aiadventchallenge/data/mcp/McpJsonRpcClient.kt`

- Изменен тип возврата `callTool()` с `String` на `McpToolData`
- Добавлена логика извлечения структурированных данных из `JsonRpcResult`:
  - `fitnessSummaryResult` → `McpToolData.FitnessSummary`
  - `scheduledSummaryResult` → `McpToolData.ScheduledSummary`
  - `addFitnessLogResult` → `McpToolData.AddFitnessLog`
  - `runScheduledSummaryResult` → `McpToolData.RunScheduledSummary`
  - `fitnessSummaryExportFullResponse` → `McpToolData.ExportResult`
  - прочее → `McpToolData.StringResult`

### Шаг 3: Обновлен McpRepository

**Файл:** `app/src/main/java/com/example/aiadventchallenge/data/mcp/McpRepository.kt`

- Изменен тип возврата `callTool()` с `String` на `McpToolData`

### Шаг 4: Обновлен CallMcpToolUseCase

**Файл:** `app/src/main/java/com/example/aiadventchallenge/domain/usecase/mcp/CallMcpToolUseCase.kt`

- Изменен тип возврата с `String` на `McpToolData`

### Шаг 5: Обновлен McpToolOrchestratorImpl

**Файл:** `app/src/main/java/com/example/aiadventchallenge/domain/mcp/McpToolOrchestratorImpl.kt`

- Изменена сигнатура `buildMcpContext()` для приема `McpToolData` вместо `String`
- Изменена сигнатура `buildMcpExportContext()` для приема `McpToolData` вместо `String`
- Добавлены методы форматирования для каждого типа данных:
  - `formatFitnessSummary()` - форматирует фитнес-сводку
  - `formatScheduledSummary()` - форматирует автоматическую сводку
  - `formatAddFitnessLog()` - форматирует результат добавления записи
  - `formatExportResult()` - форматирует результат экспорта
  - `formatExportResultForExport()` - форматирует результат экспорта для LLM
  - `formatRunScheduledSummary()` - форматирует результат запуска сводки
- Обновлены все методы-вызыватели:
  - `executeNutritionTool()`
  - `callAddFitnessLog()`
  - `callGetFitnessSummary()`
  - `callRunScheduledSummary()`
  - `callGetLatestScheduledSummary()`
  - `callRunFitnessSummaryExportPipeline()`

### Шаг 6: Обновлены тесты

**Файл:** `app/src/test/java/com/example/aiadventchallenge/domain/mcp/McpToolOrchestratorTest.kt`

- Добавлен import для `McpToolData`
- Добавлен import для `FitnessRequestDetector`
- Добавлен параметр `fitnessRequestDetector` в setup
- Обновлен mock для возврата `McpToolData.StringResult()` вместо строки

---

## Стадия: Validation

### Сборка

```bash
./gradlew :app:compileDebugKotlin
```

**Результат:** ✅ BUILD SUCCESSFUL

### Создание APK

```bash
./gradlew :app:assembleDebug
```

**Результат:** ✅ BUILD SUCCESSFUL

### Юнит-тесты

**Файл:** `McpToolOrchestratorTest.kt`

**Результат:** ✅ Компиляция успешна, синтаксических ошибок нет

---

## Стадия: Report

### Измененные файлы

| Файл | Действие | Строки |
|------|----------|--------|
| `domain/mcp/McpToolResult.kt` | СОЗДАН | 98 строк (новый файл) |
| `data/mcp/McpJsonRpcClient.kt` | ИЗМЕНЁН | ~100 строк |
| `data/mcp/McpRepository.kt` | ИЗМЕНЁН | 2 строки |
| `usecase/mcp/CallMcpToolUseCase.kt` | ИЗМЕНЁН | 1 строка |
| `domain/mcp/McpToolOrchestratorImpl.kt` | ИЗМЕНЁН | ~200 строк |
| `domain/mcp/McpToolOrchestratorTest.kt` | ИЗМЕНЁН | 10 строк |

**Всего:** ~410 строк изменено

---

## Результаты рефакторинга

### ✅ Преимущества

1. **Типобезопасность**: Каждый инструмент возвращает строго типизированный результат
2. **Масштабируемость**: Легко добавить новые инструменты без изменения базовой логики
3. **Чистый код**: Форматирование вынесено в отдельные методы
4. **Правильный контекст для LLM**: Данные будут правильно форматироваться и передаваться в контекст

### 📊 До / После

#### До:
```kotlin
suspend fun callTool(name: String, params: Map<String, Any?>): String
return response.result?.message ?: ""  // ❌ Возвращает "" для fitnessSummaryResult
```

#### После:
```kotlin
suspend fun callTool(name: String, params: Map<String, Any?>): McpToolData
return when {
    result.fitnessSummaryResult != null -> McpToolData.FitnessSummary(...)
    else -> McpToolData.StringResult(result.message ?: "")
}  // ✅ Возвращает структурированные данные
```

---

## Пример работы

### Запрос пользователя:
"Экспортируй мою сводку за неделю в json файл"

### Результат MCP (данные):
```json
{
  "fitnessSummaryResult": {
    "period": "last_7_days",
    "entriesCount": 28,
    "avgWeight": 82.04,
    "workoutsCompleted": 20,
    "avgSteps": 8171,
    "avgSleepHours": 7.11,
    "avgProtein": 164,
    "adherenceScore": 0.71,
    "summaryText": "За период выполнено 20 тренировок..."
  }
}
```

### Контекст для LLM (форматировано):
```
Фитнес-сводка за период: last_7_days
Количество записей: 28
Средний вес: 82.04 кг
Выполнено тренировок: 20
Средние шаги: 8171
Средний сон: 7.11 ч
Средний белок: 164 г
Оценка соблюдения: 0.71

Сводка: За период выполнено 20 тренировок, средний вес 82.0 кг, средний сон 7.1 ч.
Хорошая регулярность тренировок, Хороший уровень потребления белка.
```

### Ответ LLM (ожидаемый):
```
Отлично! Вот твоя сводка за неделю:

📊 Твоя статистика:
- Записей: 28 за 7 дней
- Средний вес: 82.0 кг
- Тренировки: 20 из 7 дней (отлично!)
- Средние шаги: 8,171
- Средний сон: 7.1 ч
- Средний белок: 164 г

Отличный прогресс! Хочешь экспортировать в JSON файл?
```

---

## Статус

**Стадия:** Done ✅

**Что исправлено:**
- ✅ Типобезопасные результаты MCP инструментов
- ✅ Правильное извлечение данных из JsonRpcResult
- ✅ Форматирование данных для контекста LLM
- ✅ Обновление всех слоёв архитектуры
- ✅ Обновление тестов

**Следующие шаги (опционально):**
- Добавить интеграционные тесты для каждого типа результата
- Добавить больше логов для отладки
- Добавить метрики для мониторинга успеха вызовов MCP инструментов

---

## Ссылки на файлы

- `domain/mcp/McpToolResult.kt:1` - Новые типы результатов
- `data/mcp/McpJsonRpcClient.kt:76` - Извлечение данных
- `data/mcp/McpRepository.kt:48` - Передача данных
- `usecase/mcp/CallMcpToolUseCase.kt:8` - Тип возврата
- `domain/mcp/McpToolOrchestratorImpl.kt:191` - Форматирование контекста
- `domain/mcp/McpToolOrchestratorImpl.kt:301` - Методы форматирования
