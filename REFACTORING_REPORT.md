# Рефакторинг Android приложения с MCP интеграцией

## Дата
2026-04-13

## Цель
Рефакторинг Android-приложения для упрощения архитектуры и подготовки к реализации фитнес-сценария с 3 MCP серверами.

---

## Выполненные задачи

### ✅ 1. Удаление избыточных систем

**Удалено 50+ файлов:**

#### UI экраны (12 файлов)
- McpDebugScreen
- ModelVersionsScreen
- PromptComparisonScreen
- TemperatureScreen
- Оставлен только ChatScreen

#### Task система (13 файлов)
- TaskCoordinator, TaskIntentHandler, TaskRepository
- TaskDao, TaskEntity, TaskContext, TaskPhase, TaskAction
- TaskStateMachine, TaskProtocol
- TaskPromptBuilder, EnhancedTaskAiResponse, UserResponseParser

#### Memory система (19 файлов)
- MemoryManager, MemoryConsolidator, AiMemoryClassifier
- MemoryRepository, MemoryClassificationRepository
- MemoryDao, MemoryEntity
- MemoryContext, MemoryConfig, MemoryConsolidationConfig
- MemoryClassifierConfig

#### MCP детекторы (6 файлов)
- CrossServerFlowDetector
- FitnessRequestDetector
- NutritionRequestDetector

---

### ✅ 2. Упрощение ChatViewModel

**Было:** 656 строк
**Стало:** ~350 строк

**Удалены зависимости:**
- Task, TaskContext, TaskPromptBuilder
- Memory, MemoryManager
- TaskStateMachine

**Сохранено:**
- Branching стратегия
- SlidingWindow стратегия
- StickyFacts стратегия
- MCP инструменты

---

### ✅ 3. Упрощение McpToolOrchestrator

**Было:** 461 строка
**Стало:** ~140 строк

**Удалено:**
- Детекторы запросов
- Автоматический детект инструментов

**Сохранено:**
- Ручной вызов инструментов через `executeTool()`
- Форматирование результатов
- Multi-server flow поддержка

---

### ✅ 4. Исправление ошибок компиляции

**Исправлено 8 файлов с дублированным кодом:**
- ChatMessageHandlerImpl.kt - удалена дублированная реализация (строки 230-400)
- ChatMessageHandler.kt - удалён дубликат класса ChatMessageResult
- StickyFactsStrategy.kt - удалены 3 блока дублированного кода
- BranchingStrategy.kt - удалены 2 блока дублированного кода
- SlidingWindowStrategy.kt - удалён блок дублированного кода
- ContextStrategy.kt - исправлен импорт Message (data вместо domain)
- BranchingStrategy.kt - исправлен импорт ChatRepository
- ChatViewModel.kt - удалён вызов несуществующего `initialize()`

**Результат:** ✅ BUILD SUCCESSFUL

---

### ✅ 5. Удаление ContextStrategy.MemoryBased

**Изменено:**
- ContextStrategyFactory - выбрасывает ошибку при попытке создать MemoryBasedStrategy
- Удалены все зависимости от MemoryRepository в стратегиях контекста

---

### ✅ 6. Документация

#### MCP_INTEGRATION.md
- Архитектура MCP серверов
- Подключение к MCP серверу
- Фитнес-сценарий: 3 шага (Search → Summarize → Create Reminder)
- Multi-Server Flow выполнение
- Тестирование и Troubleshooting

#### CONTEXT_STRATEGIES.md
- SlidingWindowStrategy - скользящее окно
- StickyFactsStrategy - липкие факты
- BranchingStrategy - ветвление диалогов
- Сравнение стратегий
- Примеры использования
- Тестирование

---

## Архитектура после рефакторинга

### MCP Интеграция

**3 MCP сервера:**
1. Fitness Server (8081) - фитнес-логи, сводки, расчёты
2. Reminder Server (8082) - напоминания
3. Main Server (8080) - оркестрация multi-server сценариев

**Android компоненты:**
- McpJsonRpcClient - JSON-RPC клиент
- McpRepository - операции MCP
- McpToolOrchestrator - оркестрация инструментов
- CallMcpToolUseCase - вызов инструментов

### Стратегии контекста

**Сохранены по запросу пользователя:**
- SlidingWindowStrategy - простые чаты
- StickyFactsStrategy - персонализация с фактами
- BranchingStrategy - эксперименты с ветвлением

**Удалено:**
- MemoryBasedStrategy (система памяти)

---

## Следующие шаги

### 🔧 В процессе (для тестирования)

1. **Протестировать фитнес-сценарий**
   - Запустить MCP сервер: `cd mcp-server && gradle run`
   - Запустить Android приложение
   - Отправить запрос: "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"
   - Проверить что выполняется 3 шага: Search → Summarize → Create Reminder

2. **Проверить стратегии контекста**
   - SlidingWindow: отправить много сообщений, проверить что в контексте только N последних
   - StickyFacts: отправить сообщение с личными данными, проверить что факты сохраняются
   - Branching: создать ветку диалога, переключиться, проверить контекст

---

## Статус

✅ **Основные задачи выполнены:**
- Избыточные системы удалены
- ChatViewModel и McpToolOrchestrator упрощены
- Проект успешно собирается
- Документация создана

⏳ **Ожидает тестирования:**
- Фитнес-сценарий с 3 MCP серверами
- Работа стратегий контекста

---

## Технические детали

### AppDatabase
- Версия обновлена до 7
- Удалены таблицы: memory_tasks, task_phases, task_actions, memory_facts

### Импорты и типы
- Все стратегии используют `data.model.Message` и `data.model.MessageRole`
- Удалены импорты `domain.model.Message` (не существует)
- Исправлена дублирующаяся зависимость `MessageRole` из Invariant.kt

### Файлы с ключевыми изменениями
- `ChatViewModel.kt` - упрощён с 656 до ~350 строк
- `McpToolOrchestratorImpl.kt` - упрощён с 461 до ~140 строк
- `ContextStrategyFactory.kt` - MemoryBasedStrategy выбрасывает ошибку
- `AppDatabase.kt` - версия 7, удалены memory таблицы

---

## Риски и ограничения

### Требуется MCP сервер
Для тестирования фитнес-сценария необходимо запустить MCP сервер:
```bash
cd mcp-server
gradle run
```

### Детекторы удалены
Автоматический детект MCP инструментов больше не работает.
Для вызова инструментов используйте:
```kotlin
mcpToolOrchestrator.executeTool(
    toolName = "calculate_nutrition_plan",
    params = mapOf("sex" to "male", "age" to 30, ...)
)
```

### Тесты устарели
`McpToolOrchestratorTest.kt` ссылается на удалённые детекторы.
Требуется обновление тестов для текущей архитектуры.

---

## Контакты и ресурсы

- Документация MCP: `MCP_INTEGRATION.md`
- Документация стратегий: `CONTEXT_STRATEGIES.md`
- MCP сервер: `mcp-server/`
- Android приложение: `app/src/main/java/com/example/aiadventchallenge/`
