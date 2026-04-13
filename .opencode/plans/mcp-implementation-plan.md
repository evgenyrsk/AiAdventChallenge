# План реализации MCP фитнес-сценария

## Дата
2026-04-13

## Выбранный подход
- **Автоматическая детекция** - LLM автоматически определяет когда вызывать фитнес-сценарий
- **Дополнительные функции:** индикатор соединения MCP, логи выполнения flow, ошибки детекции

---

## Текущее состояние проекта

### ✅ Что уже работает
1. **MCP сервер**
   - 3 сервера зарегистрированы (Main 8080, Fitness 8081, Reminder 8082)
   - Тестовые данные созданы (7 дней фитнес-логов)
   - `execute_multi_server_flow` инструмент реализован
   - Предопределённый фитнес flow: `createFitnessToReminderFlow()`

2. **Android приложение**
   - McpJsonRpcClient - настроен на `http://10.0.2.2:8080`
   - McpRepository - имеет `executeMultiServerFlow()` метод
   - CallMcpToolUseCase - use case для вызова инструментов
   - ChatViewModel - использует McpToolOrchestrator
   - Проект успешно собирается (BUILD SUCCESSFUL)

### ❌ Критические проблемы

1. **McpToolOrchestratorImpl.detectAndExecuteTool() всегда возвращает NoToolFound**
   - Строка 15: `return ToolExecutionResult.NoToolFound`
   - Никогда не вызывает MCP инструменты автоматически

2. **Нет UI для фитнес-сценария**
   - Нет кнопки или способа запустить multi-server flow
   - Нет индикатора соединения с MCP сервером
   - Нет отображения логов выполнения flow

3. **Нет инициализации соединения с MCP сервером**
   - `connectAndListTools()` не вызывается при запуске приложения
   - Нет проверки статуса соединения

4. **Тесты не обновлены**
   - McpToolOrchestratorTest.kt ссылается на удалённые детекторы
   - TaskIntentHandlerTest.kt ссылается на удалённые Task классы

---

## Пошаговый план реализации

### Этап 1: Исправление автоматической детекции (КРИТИЧЕСКИ ВАЖНО)

#### Задача 1.1: Реализовать автоматическую детекцию фитнес-сценария

**Цель:** Detect когда пользователь хочет использовать фитнес-сценарий

**Решение:** Обновить `McpToolOrchestratorImpl.detectAndExecuteTool()`

**План:**
1. Добавить детекцию фитнес-запросов (ключевые слова: фитнес, тренировка, лог, сводка, напоминание)
2. Если фитнес-запрос обнаружен:
   - Вызвать `mcpRepository.executeMultiServerFlow()`
   - Вернуть `ToolExecutionResult.Success()` с контекстом
3. Иначе вернуть `ToolExecutionResult.NoToolFound`

**Файл:** `app/src/main/java/com/example/aiadventchallenge/domain/mcp/McpToolOrchestratorImpl.kt`

**Код:**
```kotlin
override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
    Log.d(TAG, "🔍 Checking for MCP tool in LLM response...")

    if (isFitnessFlowRequest(userInput)) {
        Log.d(TAG, "✅ Detected fitness flow request")

        return try {
            val flowResult = callMcpToolUseCase.executeMultiServerFlow(userInput)
            ToolExecutionResult.Success(formatMultiServerFlowContext(flowResult))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute fitness flow", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    return ToolExecutionResult.NoToolFound
}

private fun isFitnessFlowRequest(input: String): Boolean {
    val keywords = listOf(
        "фитнес", "тренировк", "спорт", "workout", "fitness",
        "лог", "запис", "log",
        "сводк", "статистик", "анализ", "summary",
        "напомин", "напомни", "напомн", "напомни", "reminder"
    )
    val lowerInput = input.lowercase()
    return keywords.any { lowerInput.contains(it) }
}

private fun formatMultiServerFlowContext(result: com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult): String {
    return """
    ================================================================================
    🏋️ FITNESS MCP FLOW - ВЫПОЛНЕНИЕ СЦЕНАРИЯ
    ================================================================================

    Flow: ${result.flowName}
    Статус: ${if (result.success) "✅ Успешно" else "❌ Ошибка"}
    Шагов выполнено: ${result.stepsExecuted}/${result.totalSteps}
    Длительность: ${result.durationMs}ms

    Шаги выполнения:
    ${result.executionSteps.joinToString("\n") { step ->
        val statusEmoji = when (step.status) {
            "COMPLETED" -> "✅"
            "FAILED" -> "❌"
            "RUNNING" -> "⏳"
            else -> "⏭️"
        }
        "$statusEmoji ${step.serverId} → ${step.toolName} (${step.durationMs}ms)"
    }}

    ${if (result.errorMessage != null) "❌ Ошибка: ${result.errorMessage}" else ""}

    ================================================================================
    """.trimIndent()
}
```

**Тестирование:**
- Отправить: "Найди последние фитнес логи за неделю"
- Ожидать: вызов `execute_multi_server_flow`

---

### Этап 2: Инициализация соединения с MCP сервером

#### Задача 2.1: Добавить инициализацию при запуске приложения

**Цель:** Установить соединение с MCP сервером при запуске

**План:**
1. Создать `McpConnectionViewModel` для управления состоянием соединения
2. В `MainActivity.onCreate()` или `ChatViewModel.init()` вызвать `connectAndListTools()`
3. Сохранить статус соединения в StateFlow

**Файл:** `app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/ChatViewModel.kt`

**Изменения:**
```kotlin
// Добавить в ChatViewModel
private val _mcpConnectionStatus = MutableStateFlow<McpConnectionStatus>(McpConnectionStatus.DISCONNECTED)
val mcpConnectionStatus: StateFlow<McpConnectionStatus> = _mcpConnectionStatus.asStateFlow()

// В init() добавить
init {
    initializeMcpConnection()
    loadMessagesFromDatabase()
    // ...
}

private fun initializeMcpConnection() {
    viewModelScope.launch {
        try {
            val result = AppDependencies.mcpRepository.connectAndListTools()
            _mcpConnectionStatus.value = when {
                result.isConnected -> McpConnectionStatus.CONNECTED
                else -> McpConnectionStatus.DISCONNECTED
            }
            Log.d(TAG, "🔗 MCP Connection: ${result.isConnected}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ MCP Connection failed", e)
            _mcpConnectionStatus.value = McpConnectionStatus.DISCONNECTED
        }
    }
}
```

---

### Этап 3: UI улучшения

#### Задача 3.1: Добавить индикатор соединения MCP

**Цель:** Показывать статус подключения к MCP серверу

**План:**
1. Добавить значок в TopAppBar (connected/disconnected)
2. Использовать разные цвета для статусов

**Файл:** `app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/ChatScreen.kt`

**Код:**
```kotlin
// Добавить в ChatScreen
val mcpConnectionStatus by viewModel.mcpConnectionStatus.collectAsStateWithLifecycle()

// В TopAppBar добавить IconButton
IconButton(
    onClick = { /* показать детали */ }
) {
    Icon(
        imageVector = if (mcpConnectionStatus == McpConnectionStatus.CONNECTED) {
            Icons.Default.CheckCircle
        } else {
            Icons.Default.Info
        },
        contentDescription = "MCP Connection Status",
        tint = if (mcpConnectionStatus == McpConnectionStatus.CONNECTED) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        }
    )
}
```

#### Задача 3.2: Добавить логи выполнения flow

**Цель:** Показывать пошаговое выполнение фитнес-сценария

**План:**
1. Добавить состояние для хранения flow результатов
2. Показывать детальный отчёт о выполнении в диалоге

**Файл:** `app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/ChatScreen.kt`

**Код:**
```kotlin
// Добавить в ChatViewModel
private val _lastFlowResult = MutableStateFlow<MultiServerFlowResult?>(null)
val lastFlowResult: StateFlow<MultiServerFlowResult?> = _lastFlowResult.asStateFlow()

// В ChatScreen добавить диалог
val lastFlowResult by viewModel.lastFlowResult.collectAsStateWithLifecycle()

if (lastFlowResult != null) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissFlowResult() },
        title = { Text("Результат выполнения MCP Flow") },
        text = {
            Column {
                Text("Flow: ${lastFlowResult.flowName}")
                Text("Статус: ${if (lastFlowResult.success) "✅" else "❌"}")
                Text("Шаги: ${lastFlowResult.stepsExecuted}/${lastFlowResult.totalSteps}")

                Divider()

                lastFlowResult.executionSteps.forEach { step ->
                    Row {
                        Text("${step.status} ${step.toolName}")
                        Text("${step.durationMs}ms")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.dismissFlowResult() }) {
                Text("Закрыть")
            }
        }
    )
}
```

#### Задача 3.3: Добавить обработку ошибок детекции

**Цель:** Показывать ошибки когда MCP инструменты недоступны

**План:**
1. В `ChatViewModel.sendMessage()` добавить обработку `ToolExecutionResult.Error`
2. Показывать системное сообщение с ошибкой

**Файл:** `app/src/main/java/com/example/aiadventchallenge/ui/screens/chat/ChatViewModel.kt`

**Изменения:**
```kotlin
val mcpToolResult = mcpToolOrchestrator.detectAndExecuteTool(userInput)

val mcpContext = when (mcpToolResult) {
    is ToolExecutionResult.Success -> {
        // Сохранить результат flow для отображения
        _lastFlowResult.value = extractFlowResult(mcpToolResult.context)
        mcpToolResult.context
    }
    is ToolExecutionResult.NoToolFound -> null
    is ToolExecutionResult.Error -> {
        Log.e(TAG, "❌ MCP tool error: ${mcpToolResult.message}")
        addSystemMessage("Ошибка MCP: ${mcpToolResult.message}")
        null
    }
}
```

---

### Этап 4: Тестирование

#### Задача 4.1: Обновить тесты

**Цель:** Исправить тесты после удаления детекторов

**План:**
1. Удалить `McpToolOrchestratorTest.kt` (ссылается на удалённые детекторы)
2. Удалить `TaskIntentHandlerTest.kt` (ссылается на удалённые Task классы)
3. Создать новый тест для автоматической детекции

**Файл:** `app/src/test/java/com/example/aiadventchallenge/domain/mcp/FitnessFlowDetectionTest.kt`

**Код:**
```kotlin
class FitnessFlowDetectionTest {
    private lateinit var orchestrator: McpToolOrchestratorImpl

    @Before
    fun setup() {
        val callMcpToolUseCase = mockk(relaxed = true)
        orchestrator = McpToolOrchestratorImpl(callMcpToolUseCase)
    }

    @Test
    fun `detect fitness flow request - should return success`() = runTest {
        val userInput = "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun `no fitness request - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)
    }
}
```

---

### Этап 5: Интеграционное тестирование

#### Задача 5.1: Тестирование фитнес-сценария

**Шаги:**
1. Запустить MCP сервер:
   ```bash
   cd mcp-server
   ./gradlew run
   ```
2. Запустить Android приложение в эмуляторе
3. Проверить индикатор соединения MCP (должен быть Connected)
4. Отправить запрос: "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"
5. Проверить что:
   - В логах видно вызов `execute_multi_server_flow`
   - Выполняются 3 шага: search → summarize → create_reminder
   - Показывается диалог с результатами выполнения
   - В чате добавляется ответ LLM с MCP контекстом

#### Задача 5.2: Тестирование стратегий контекста

**SlidingWindow:**
1. Отправить 20 сообщений
2. Установить windowSize = 10
3. Проверить что в контексте только 10 последних

**StickyFacts:**
1. Отправить "Меня зовут Алексей, я хочу набрать массу"
2. Создать несколько сообщений
3. Проверить что в контексте сохранены факты

**Branching:**
1. Создать ветку от сообщения #5
2. Переключиться на новую ветку
3. Проверить что контекст соответствует ветке

---

## Резюме

### Обязательные задачи (P0)
1. ✅ Реализовать автоматическую детекцию в `McpToolOrchestratorImpl`
2. ✅ Добавить инициализацию соединения с MCP сервером
3. ✅ Добавить индикатор соединения в UI
4. ✅ Обновить тесты (удалить устаревшие, добавить новые)

### Желательные задачи (P1)
5. 📋 Добавить логи выполнения flow в UI
6. 📋 Добавить обработку ошибок детекции
7. 📋 Интеграционное тестирование

---

## Риски

### MCP сервер недоступен
**Риск:** Сервер не запущен или URL неправильный

**Митигация:** Индикатор соединения показывает статус, ошибки отображаются в UI

### Детекция работает некорректно
**Риск:** Ложные срабатывания или пропуски фитнес-запросов

**Митиграция:** Тестирование на различных примерах запросов

### Производительность
**Риск:** Multi-server flow занимает много времени

**Митигация:** Показывать индикатор загрузки, логи выполнения

---

## Дополнительные ресурсы

- MCP документация: `MCP_INTEGRATION.md`
- Стратегии контекста: `CONTEXT_STRATEGIES.md`
- Отчёт о рефакторинге: `REFACTORING_REPORT.md`
- MultiServerOrchestrator: `mcp-server/src/main/kotlin/com/example/mcp/server/orchestration/MultiServerOrchestrator.kt`
