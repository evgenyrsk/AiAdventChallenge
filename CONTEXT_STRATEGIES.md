# Тестирование Стратегий Контекста

## Стратегии контекста в проекте

### 1. SlidingWindowStrategy

**Описание:** Стратегия скользящего окна - показывает последние N сообщений.

**Конфигурация:**
- `windowSize` - количество сообщений в контексте
- `filteredMessagesCount` - количество отфильтрованных сообщений

**Пример использования:**
```kotlin
val config = ContextStrategyConfig(
    strategyType = ContextStrategyType.SLIDING_WINDOW,
    windowSize = 10
)

val strategy = SlidingWindowStrategy(config)
val messages = strategy.buildContext(
    chatId = "main",
    messages = allMessages,
    systemPrompt = "Ты фитнес-ассистент"
)
// Результат: последние 10 сообщений + system prompt
```

**Логи:**
```
📊 SlidingWindow context:
  Total messages: 50
  Window size: 10
  Messages in context: 10
  Messages filtered: 40
```

---

### 2. StickyFactsStrategy

**Описание:** Стратегия с липкими фактами - сохраняет важные факты + показывает последние N сообщений.

**Конфигурация:**
- `windowSize` - количество сообщений в окне
- `factRepository` - репозиторий для хранения фактов
- `factExtractor` - экстрактор фактов из сообщений

**Пример использования:**
```kotlin
val config = ContextStrategyConfig(
    strategyType = ContextStrategyType.STICKY_FACTS,
    windowSize = 10
)

val strategy = StickyFactsStrategy(
    config = config,
    factRepository = factRepository,
    factExtractor = factExtractor
)

val messages = strategy.buildContext(
    chatId = "main",
    messages = allMessages,
    systemPrompt = "Ты фитнес-ассистент"
)
// Результат:
// 1. System prompt
// 2. Known facts about conversation
// 3. Последние 10 сообщений
```

**Логи:**
```
📝 StickyFacts context:
  Total messages: 50
  Window size: 10
  Messages in context: 10
  Messages filtered: 40
```

**Пример сохранённых фактов:**
```
Known facts about conversation:
user_name: Алексей
user_goal: Набор массы
user_weight: 85 кг
```

**Обновление фактов:**
```kotlin
strategy.onConversationPair(userMessage, assistantMessage)
// Автоматически извлекает и сохраняет факты
```

---

### 3. BranchingStrategy

**Описание:** Стратегия с ветвлением - поддерживает параллельные ветки диалога.

**Конфигурация:**
- `windowSize` - размер окна (не используется в текущей реализации)
- `branchRepository` - репозиторий для управления ветками
- `chatRepository` - репозиторий сообщений

**Пример использования:**
```kotlin
val config = ContextStrategyConfig(
    strategyType = ContextStrategyType.BRANCHING,
    windowSize = 100 // игнорируется в BranchingStrategy
)

val strategy = BranchingStrategy(
    config = config,
    branchRepository = branchRepository,
    chatRepository = chatRepository
)

val messages = strategy.buildContext(
    chatId = "branch_1",
    messages = allMessages,
    systemPrompt = "Ты фитнес-ассистент"
)
// Результат:
// 1. System prompt
// 2. Все сообщения из активной ветки (branch_1)
```

**Логи:**
```
📊 Branching context:
  Active branch: branch_1
  Total messages in DB: 150
  Messages in active path: 25
```

**Управление ветками:**
```kotlin
// Создать новую ветку
branchRepository.createBranch(ChatBranch(
    id = "branch_1",
    name = "Вариант 1",
    parentId = "main",
    lastMessageId = "msg_5"
))

// Активировать ветку
branchRepository.setActiveBranchId("branch_1")
```

---

## Сравнение стратегий

| Характеристика        | SlidingWindow | StickyFacts | Branching |
|----------------------|---------------|-------------|-----------|
| Контекст              | Последние N   | Факты + N   | Вся ветка |
| Память               | Нет           | Да          | Нет       |
| Многопоточность       | Нет           | Нет         | Да        |
| Сложность             | Низкая        | Средняя     | Высокая   |
| Использование         | Простые чаты  | Персонализация | Эксперименты |

---

## Тестирование

### Тест 1: SlidingWindowStrategy

**Сценарий:** Проверить что стратегия показывает только последние N сообщений

**Шаги:**
1. Создать 50 сообщений в чате
2. Установить `windowSize = 10`
3. Вызвать `buildContext()`
4. Проверить что в контексте только 10 последних сообщений

**Ожидаемый результат:**
- `messages.size = 50`
- `context.size = 11` (1 system prompt + 10 сообщений)
- `filteredMessagesCount = 40`

---

### Тест 2: StickyFactsStrategy

**Сценарий:** Проверить сохранение и извлечение фактов

**Шаги:**
1. Отправить сообщение "Меня зовут Алексей, я хочу набрать массу"
2. Вызвать `onConversationPair()` для сохранения фактов
3. Создать новые сообщения (долгий диалог)
4. Вызвать `buildContext()`
5. Проверить что факты присутствуют в контексте

**Ожидаемый результат:**
- В контексте есть "Known facts about conversation:"
- Факты содержат: `user_name: Алексей`, `user_goal: Набор массы`
- Количество сообщений = `1 system prompt + 1 facts + N messages`

---

### Тест 3: BranchingStrategy

**Сценарий:** Проверить работу с ветками

**Шаги:**
1. Создать ветку "main" с 10 сообщениями
2. Создать ветку "branch_1" от сообщения #5
3. Добавить 5 сообщений в "branch_1"
4. Активировать "branch_1"
5. Вызвать `buildContext()` для "branch_1"
6. Проверить контекст

**Ожидаемый результат:**
- Контекст содержит только сообщения из "branch_1"
- Сообщения #1-#5 из "main" + 5 новых из "branch_1"
- `activeBranchId = branch_1`
- `messagesInContext = 10`

---

## Запуск тестов

### В коде приложения

```kotlin
// Тест SlidingWindowStrategy
val slidingWindow = SlidingWindowStrategy(
    config = ContextStrategyConfig(
        strategyType = ContextStrategyType.SLIDING_WINDOW,
        windowSize = 10
    )
)

val context = slidingWindow.buildContext(
    chatId = "main",
    messages = messages,
    systemPrompt = "Ты фитнес-ассистент"
)

println("Контекст: ${context.size} сообщений")
println("Отфильтровано: ${slidingWindow.getDebugInfo()["filteredMessagesCount"]}")
```

### В ChatViewModel

Стратегии автоматически используются в `ChatViewModel`:

```kotlin
val strategy = contextStrategyFactory.create(
    chatSettingsRepository.getSettings()
)

val apiMessages = strategy.buildContext(
    chatId = activeBranchId,
    messages = activeMessages,
    systemPrompt = config.systemPrompt
)
```

---

## Мониторинг и отладка

### Debug информация

Каждая стратегия предоставляет `getDebugInfo()`:

```kotlin
val debugInfo = strategy.getDebugInfo()
println(debugInfo)
```

**SlidingWindow:**
```kotlin
{
  "strategy": "SlidingWindow",
  "windowSize": 10,
  "filteredMessagesCount": 40,
  "messagesInContext": 10
}
```

**StickyFacts:**
```kotlin
{
  "strategy": "StickyFacts",
  "windowSize": 10,
  "filteredMessagesCount": 40,
  "messagesInContext": 10
}
```

**Branching:**
```kotlin
{
  "strategy": "Branching",
  "activeBranchId": "branch_1",
  "totalMessages": 150,
  "messagesInContext": 25
}
```

---

## Настройка через UI

В приложении можно переключать стратегии через настройки:

```kotlin
fun setContextStrategy(strategyType: ContextStrategyType) {
    viewModelScope.launch {
        val currentSettings = chatSettingsRepository.getSettings()
        val newSettings = currentSettings.copy(
            contextStrategyType = strategyType
        )
        chatSettingsRepository.updateSettings(newSettings)
    }
}
```

---

## Troubleshooting

### Слишком большой контекст

**Проблема:** LLM не может обработать контекст

**Решение:**
- Уменьшите `windowSize` в SlidingWindowStrategy или StickyFactsStrategy
- Используйте SlidingWindow вместо Branching если контекст слишком большой

### Факты не сохраняются

**Проблема:** StickyFacts не сохраняет факты

**Решение:**
- Убедитесь что `onConversationPair()` вызывается после каждого ответа
- Проверьте что `factExtractor` работает корректно
- Проверьте логи: "❌ StickyFacts: Failed to extract facts"

### Ветка не переключается

**Проблема:** BranchingStrategy показывает сообщения из другой ветки

**Решение:**
- Убедитесь что `setActiveBranchId()` вызывается
- Проверьте что `branchRepository.getActiveBranchId()` возвращает правильный ID
- Убедитесь что чекпоинты созданы корректно
