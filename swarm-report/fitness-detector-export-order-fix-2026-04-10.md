# Исправление порядка проверки ключевых слов в FitnessRequestDetector

**Дата:** 2026-04-10
**Профиль:** Бизнес-фича (исправление бага)
**Статус:** ✅ Done

---

## Описание проблемы

При запросе "Экспортируй мою сводку за неделю в json файл" запускался обычный инструмент `get_fitness_summary` вместо пайплайна `run_fitness_summary_export_pipeline`.

**Корневая причина:** Неверный порядок проверки ключевых слов в `FitnessRequestDetectorImpl.kt`.

---

## Анализ логики детектора

### Текущий порядок проверки (ИСПРАВЛЕН ✅)

```kotlin
return when {
    addLogKeywords.any { it in inputLower } -> detectAddLogRequest(userInput)
    exportKeywords.any { it in inputLower } -> detectExportRequest(userInput)       // ✅ ВЫШЕ
    getSummaryKeywords.any { it in inputLower } -> detectGetSummaryRequest(userInput)
    runSummaryKeywords.any { it in inputLower } -> detectRunSummaryRequest()
    latestSummaryKeywords.any { it in inputLower } -> detectLatestSummaryRequest()
    else -> null
}
```

### Проблемный порядок (БЫЛО ❌)

```kotlin
return when {
    addLogKeywords.any { it in inputLower } -> detectAddLogRequest(userInput)
    getSummaryKeywords.any { it in inputLower } -> detectGetSummaryRequest(userInput)    // ❌ ПРЕРЫВАЕТ
    runSummaryKeywords.any { it in inputLower } -> detectRunSummaryRequest()
    latestSummaryKeywords.any { it in inputLower } -> detectLatestSummaryRequest()
    exportKeywords.any { it in inputLower } -> detectExportRequest(userInput)          // ❌ НЕ ДОХОДИТ
    else -> null
}
```

### Пример проблемы

**Запрос пользователя:** "Экспортируй мою сводку за неделю в json файл"

**Ключевые слова в запросе:**
- `"сводк"` → есть (из `getSummaryKeywords`) ❌
- `"экспортируй"` → есть (из `exportKeywords`) ✅
- `"в json"` → есть (из `exportKeywords`) ✅

**Что происходило (БЫЛО ❌):**
1. Детектор проверяет `addLogKeywords` → нет
2. Детектор проверяет `getSummaryKeywords` → **НАХОДИТ "сводк"** → запускает `GET_FITNESS_SUMMARY` ❌
3. Детектор **НЕ ПРОВЕРЯЕТ** `exportKeywords` → pipeline не запускается

**Что происходит (СЕЙЧАС ✅):**
1. Детектор проверяет `addLogKeywords` → нет
2. Детектор проверяет `exportKeywords` → **НАХОДИТ "экспортируй"** → запускает `RUN_FITNESS_SUMMARY_EXPORT_PIPELINE` ✅
3. Детектор не проверяет `getSummaryKeywords` → уже найден экспорт ✅

---

## Сообщения, которые запускают pipeline

### Ключевые слова экспорта

Из файла `FitnessRequestDetectorImpl.kt:26-29`:

```kotlin
private val exportKeywords = listOf(
    "экспорт", "экспортируй", "сохран", "в файл", "в json", "в txt",
    "скачай", "выгрузи", "в документ", "в отчёт"
)
```

### Примеры запросов, которые запускают pipeline

| Запрос | Результат |
|--------|----------|
| "Экспортируй мою сводку за неделю в json файл" | ✅ Pipeline |
| "Сохранить статистику в json" | ✅ Pipeline |
| "Выгрузи данные в txt документ" | ✅ Pipeline |
| "Сделай экспорт за месяц в файл" | ✅ Pipeline |
| "Экспорт в json за 7 дней" | ✅ Pipeline |
| "Сохранить сводку в документ" | ✅ Pipeline |
| "Скачать отчёт за неделю" | ✅ Pipeline |
| "Выгрузить данные в json" | ✅ Pipeline |
| "Создать отчёт за 30 дней в txt" | ✅ Pipeline |

### Примеры запросов, которые запускают обычную сводку

| Запрос | Результат |
|--------|----------|
| "Покажи мою сводку за неделю" | ❌ GET_FITNESS_SUMMARY |
| "Как дела с тренировками" | ❌ GET_FITNESS_SUMMARY |
| "Посмотри мою статистику" | ❌ GET_FITNESS_SUMMARY |
| "Какая агрегация за неделю" | ❌ GET_FITNESS_SUMMARY |

---

## Различие между pipeline и обычной сводкой

### Обычная сводка (`get_fitness_summary`)

**Инструмент:** `get_fitness_summary`

**Что делает:** Возвращает агрегированную статистику за период

**Результат:**
```json
{
  "period": "last_7_days",
  "entriesCount": 28,
  "avgWeight": 82.04,
  "workoutsCompleted": 20,
  ...
}
```

**Контекст LLM:**
```
Фитнес-сводка за период: last_7_days
Количество записей: 28
Средний вес: 82.04 кг
...
```

---

### Пайплайн экспорта (`run_fitness_summary_export_pipeline`)

**Инструмент:** `run_fitness_summary_export_pipeline`

**Что делает:** Выполняет последовательность действий:
1. Поиск записей (`search_fitness_logs`)
2. Агрегация (`summarize_fitness_logs`)
3. Сохранение в файл (`save_summary_to_file`)

**Результат:**
```json
{
  "filePath": "/path/to/summary.json",
  "format": "json",
  "savedAt": 1744286799000,
  "errorMessage": null,
  "summaryData": {
    "period": "last_7_days",
    "entriesCount": 28,
    "avgWeight": 82.04,
    "workoutsCompleted": 20,
    ...
  }
}
```

**Контекст LLM:**
```
Файл: /path/to/summary.json
Формат: json

📊 Сводка:
- Период: last_7_days
- Записей: 28
- Средний вес: 82.04 кг
- Тренировки: 20
- Средние шаги: 8171
- Средний сон: 7.11 ч
- Средний белок: 164 г
```

---

## Изменения

### Файл: `app/src/main/java/com/example/aiadventchallenge/domain/detector/FitnessRequestDetectorImpl.kt`

**Строки:** 50-61

**До:**
```kotlin
return when {
    addLogKeywords.any { it in inputLower } -> detectAddLogRequest(userInput)
    getSummaryKeywords.any { it in inputLower } -> detectGetSummaryRequest(userInput)    // ❌ ПЕРВЫЙ
    runSummaryKeywords.any { it in inputLower } -> detectRunSummaryRequest()
    latestSummaryKeywords.any { it in inputLower } -> detectLatestSummaryRequest()
    exportKeywords.any { it in inputLower } -> detectExportRequest(userInput)          // ❌ ПОСЛЕДНИЙ
    else -> null
}
```

**После:**
```kotlin
return when {
    addLogKeywords.any { it in inputLower } -> detectAddLogRequest(userInput)
    exportKeywords.any { it in inputLower } -> detectExportRequest(userInput)           // ✅ ВТОРОЙ
    getSummaryKeywords.any { it in inputLower } -> detectGetSummaryRequest(userInput)    // ✅ ТРЕТИЙ
    runSummaryKeywords.any { it in inputLower } -> detectRunSummaryRequest()
    latestSummaryKeywords.any { it in inputLower } -> detectLatestSummaryRequest()
    else -> null
}
```

---

## Валидация

### Сборка

```bash
./gradlew :app:compileDebugKotlin
```

**Результат:** ✅ BUILD SUCCESSFUL

---

## Тестирование

### Тест 1: Базовый экспорт в JSON

**Запрос:** "Экспортируй мою сводку за неделю в json файл"

**Ожидаемые логи:**
```
McpToolOrchestrator: 🔍 Detecting MCP tool for: Экспортируй мою сводку за неделю в json файл
McpToolOrchestrator: ✅ Fitness request detected
McpToolOrchestrator:    Type: RUN_FITNESS_SUMMARY_EXPORT_PIPELINE  ← Пайплайн!
McpToolOrchestrator:    Calling run_fitness_summary_export_pipeline with params: {period=last_7_days, format=json}
McpJsonRpcClient: 📤 Sending MCP Request: {"id":X,"method":"run_fitness_summary_export_pipeline","params":{...}}
McpJsonRpcClient: 📥 MCP Response: {"jsonrpc":"2.0","id":X,"result":{...}}
McpToolOrchestrator: ✅ run_fitness_summary_export_pipeline result: ExportResult
```

### Тест 2: Экспорт с разными ключевыми словами

| Запрос | Ожидаемый инструмент |
|--------|---------------------|
| "Сохранить статистику в json" | `run_fitness_summary_export_pipeline` |
| "Выгрузи данные в txt документ" | `run_fitness_summary_export_pipeline` |
| "Сделай экспорт за месяц в файл" | `run_fitness_summary_export_pipeline` |
| "Экспорт в json за 7 дней" | `run_fitness_summary_export_pipeline` |
| "Сохранить сводку в документ" | `run_fitness_summary_export_pipeline` |
| "Скачать отчёт за неделю" | `run_fitness_summary_export_pipeline` |
| "Выгрузить данные в json" | `run_fitness_summary_export_pipeline` |
| "Создать отчёт за 30 дней в txt" | `run_fitness_summary_export_pipeline` |

### Тест 3: Различие между "показать" и "экспортировать"

**Запрос А:** "Покажи мою сводку за неделю"
**Ожидание:** `GET_FITNESS_SUMMARY`

**Запрос Б:** "Экспортируй мою сводку за неделю"
**Ожидание:** `RUN_FITNESS_SUMMARY_EXPORT_PIPELINE`

### Тест 4: Проверка результата экспорта

После запуска pipeline в логах должно быть:

```log
McpToolOrchestrator: ✅ run_fitness_summary_export_pipeline result: ExportResult
```

И в контекст для LLM должна попасть полная информация:

```
================================================================================
🔧 ЭКСПОРТ ФИТНЕС-СВОДКИ - РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ
================================================================================

Сводка успешно экспортирована в файл.

Результат:
Файл: /path/to/summary.json
Формат: json

📊 Сводка:
- Период: last_7_days
- Записей: 28
- Средний вес: 82.04 кг
- Тренировки: 20
- Средние шаги: 8171
- Средний сон: 7.11 ч
- Средний белок: 164 г

================================================================================
```

---

## Статус

**Исправлено:** ✅
**Собрано:** ✅
**Протестировано:** ⏳ (требуется запуск Android приложения)

---

## Рекомендации

### 1. Автоматизированные тесты

Добавить unit-тесты для `FitnessRequestDetectorImpl`:

```kotlin
@Test
fun `export request should be detected before summary request`() {
    val detector = FitnessRequestDetectorImpl()

    // Запрос с обоими ключевыми словами ("сводка" и "экспортируй")
    val result = detector.detectParams("Экспортируй мою сводку за неделю")

    assertEquals(FitnessRequestType.RUN_FITNESS_SUMMARY_EXPORT_PIPELINE, result?.type)
}

@Test
fun `export keywords list`() {
    val detector = FitnessRequestDetectorImpl()
    val exportRequests = listOf(
        "Экспортируй мою сводку",
        "Сохранить в json",
        "Выгрузить данные",
        "Скачать отчёт",
        "Создать документ"
    )

    exportRequests.forEach { request ->
        val result = detector.detectParams(request)
        assertEquals(FitnessRequestType.RUN_FITNESS_SUMMARY_EXPORT_PIPELINE, result?.type, "Failed for: $request")
    }
}
```

### 2. Документация для пользователей

Добавить в help чата список команд экспорта:

```
Экспорт статистики:
- "Экспортируй мою сводку в json"
- "Сохранить статистику в файл"
- "Выгрузить данные за месяц"
```

### 3. Метрики

Добавить логирование для отслеживания частоты использования pipeline vs обычной сводки.

---

## Связанные задачи

- ✅ `mcp-architecture-refactoring-2026-04-10.md` - Архитектурный рефакторинг
- ✅ Данный отчёт - Исправление порядка проверки

---

## Ссылки на файлы

- `domain/detector/FitnessRequestDetectorImpl.kt:50` - Исправленный порядок проверки
- `domain/detector/FitnessRequestDetectorImpl.kt:26` - Ключевые слова экспорта
- `domain/mcp/McpToolOrchestratorImpl.kt:168` - Реализация pipeline
