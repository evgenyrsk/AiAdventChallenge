# Контекст стратегии управления контекстом

## Обзор

В приложении реализована архитектура для управления контекстом диалога с использованием стратегии. Это позволяет легко переключаться между различными подходами к управлению контекстом.

## Реализованные стратегии

### 1. Sliding Window

**Описание:** Сохраняет только последние N сообщений для контекста.

**Файл:** `domain/context/SlidingWindowStrategy.kt`

**Поведение:**
- Берёт последние `windowSize` сообщений из истории
- Включает системный prompt
- Старые сообщения не попадают в запрос к LLM

**Настройка:**
- `windowSize`: количество сообщений для хранения (по умолчанию: 10)

### 2. Sticky Facts

**Описание:** Извлекает и хранит ключевые факты из диалога.

**Файл:** `domain/context/StickyFactsStrategy.kt`

**Поведение:**
- После каждого сообщения пользователя извлекает факты
- Хранит факты в структурированном виде
- Включает факты в контекст как отдельное system сообщение
- Также включает последние `windowSize` сообщений

**Настройка:**
- `windowSize`: количество последних сообщений для контекста
- `facts`: список извлечённых фактов

**Модель факта:**
```kotlin
data class FactEntry(
    val key: String,
    val value: String,
    val source: FactSource, // EXTRACTED, MANUAL, SYSTEM
    val updatedAt: Long,
    val confidence: Float?,
    val isOptional: Boolean
)
```

### 3. Branching

**Описание:** Позволяет создавать альтернативные ветки диалога.

**Файл:** `domain/context/BranchingStrategy.kt`

**Поведение:**
- Можно создать checkpoint в диалоге
- От checkpoint можно создать 2 независимые ветки
- Каждая ветка продолжается отдельно
- Можно переключаться между ветками

**Настройка:**
- `windowSize`: количество сообщений для контекста в активной ветке
- `branches`: список веток
- `activeBranchId`: ID активной ветки

**Модель ветки:**
```kotlin
data class ChatBranch(
    val id: String,
    val parentBranchId: String?,
    val checkpointMessageId: String,
    val title: String,
    val createdAt: Long
)
```

## Архитектура

### Интерфейс стратегии

```kotlin
interface ContextStrategy {
    suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message>

    suspend fun onUserMessage(message: ChatMessage)
    suspend fun onAssistantMessage(message: ChatMessage)
    fun getDebugInfo(): Map<String, Any>
}
```

### Фабрика стратегий

```kotlin
class ContextStrategyFactory(
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository
) {
    fun create(config: ContextStrategyConfig): ContextStrategy
}
```

### Конфигурация стратегии

```kotlin
data class ContextStrategyConfig(
    val type: ContextStrategyType,
    val windowSize: Int = 10,
    val settingsJson: String? = null
)
```

### Типы стратегий

```kotlin
enum class ContextStrategyType {
    SLIDING_WINDOW,
    STICKY_FACTS,
    BRANCHING
}
```

## База данных

### Таблицы

**chat_messages:**
- `id` (String, PRIMARY KEY)
- `content` (String)
- `isFromUser` (Boolean)
- `timestamp` (Long)
- `promptTokens` (Int?)
- `completionTokens` (Int?)
- `totalTokens` (Int?)
- `branchId` (String?) - NEW

**facts:**
- `key` (String, PRIMARY KEY)
- `value` (String)
- `source` (String)
- `updatedAt` (Long)
- `confidence` (Float?)
- `isOptional` (Boolean)

**branches:**
- `id` (String, PRIMARY KEY)
- `parentBranchId` (String?)
- `checkpointMessageId` (String)
- `title` (String)
- `createdAt` (Long)
- `isActive` (Boolean)

**chat_settings:**
- `id` (Integer, PRIMARY KEY)
- `strategyType` (String)
- `windowSize` (Integer)
- `settingsJson` (String?)

## Миграции базы данных

### Migration 3 → 4
- Добавлена таблица `facts`
- Добавлена таблица `branches`
- Добавлена таблица `chat_settings`
- Добавлено поле `branchId` в таблицу `chat_messages`
- Вставлены настройки по умолчанию

### Migration 4 → 5
- Зарезервирована для будущих изменений

## Репозитории

### Domain репозитории (интерфейсы)
- `FactRepository` - управление фактами
- `BranchRepository` - управление ветками
- `ChatSettingsRepository` - управление настройками стратегии

### Data репозитории (реализации)
- `FactRepositoryImpl`
- `BranchRepositoryImpl`
- `ChatSettingsRepository`

## Извлечение фактов

**Файл:** `domain/context/FactExtractor.kt`

**Процесс:**
1. Получает текущее сообщение пользователя
2. Получает существующие факты
3. Формирует prompt для LLM
4. Возвращает обновлённый набор фактов в формате JSON

**Формат ответа:**
```json
{
  "facts": [
    {"key": "fact_key", "value": "fact_value", "source": "EXTRACTED", "updatedAt": 1234567890, "confidence": 0.9, "isOptional": false}
  ],
  "action": "update/create/delete"
}
```

## Использование в UI

### AppBar с отображением текущей стратегии

**Файл:** `ui/screens/chat/ChatScreen.kt`

**Функционал:**
- Отображение названия текущей стратегии в AppBar
- Кнопка настроек стратегии в правой части AppBar

### Переключатель стратегий

**Файл:** `ui/screens/chat/components/StrategySettingsBottomSheet.kt`

**Функционал:**
- Выбор стратегии из списка
- Настройка размера окна (Slider: 5-50)
- Отображение текущей стратегии
- Применение настроек

**Поведение при смене стратегии:**
1. Пользователь выбирает новую стратегию
2. Если новая стратегия отличается от текущей, показывается диалог подтверждения
3. При подтверждении:
   - Чат очищается
   - Факты и ветки сбрасываются
   - Новая стратегия сохраняется
   - Начинается новый диалог

### Debug информация

**Файл:** `ui/screens/chat/ChatScreen.kt`

**Доступна через:**
- Клик на иконку ℹ️ → статистика токенов
- Длинный клик → debug лог

## Как добавить новую стратегию

1. Создайте класс, реализующий `ContextStrategy`
2. Добавьте новый тип в `ContextStrategyType`
3. Обновите `ContextStrategyFactory` для создания новой стратегии
4. (Опционально) Создайте необходимые таблицы в БД
5. (Опционально) Создайте репозитории для управления данными
6. Добавьте UI для настройки стратегии
7. Обновите документацию

## Ограничения

### Sticky Facts
- Извлечение фактов зависит от качества LLM
- При ошибках парсинга JSON используется предыдущее состояние
- Факты не удаляются автоматически

### Branching
- Копирование сообщений между ветками может занимать много памяти
- Нет визуального отображения древовидной структуры
- Переключение веток пересобирает контекст

### Общие
- Все стратегии используют один размер окна (`windowSize`)
- Нет автоматической очистки старых данных
- История сообщений хранится полностью (даже если не используется)

## Примеры использования

### Sliding Window
```kotlin
val config = ContextStrategyConfig(
    type = ContextStrategyType.SLIDING_WINDOW,
    windowSize = 10
)
val strategy = contextStrategyFactory.create(config)
val context = strategy.buildContext(null, messages, systemPrompt)
```

### Sticky Facts
```kotlin
val config = ContextStrategyConfig(
    type = ContextStrategyType.STICKY_FACTS,
    windowSize = 10
)
val strategy = contextStrategyFactory.create(config)

// После сообщения пользователя
strategy.onUserMessage(userMessage)

// Получение контекста с фактами
val context = strategy.buildContext(null, messages, systemPrompt)
```

### Branching
```kotlin
// Создание новой ветки
val newBranch = ChatBranch(
    id = "branch_${System.currentTimeMillis()}",
    parentBranchId = currentBranch.id,
    checkpointMessageId = messageId,
    title = "Альтернативный сценарий"
)
branchRepository.createBranch(newBranch)
branchRepository.setActiveBranchId(newBranch.id)

// Получение контекста активной ветки
val branchMessages = chatRepository.getMessagesByBranch(newBranch.id)
val context = strategy.buildContext(null, branchMessages, systemPrompt)
```

## Следующие шаги

1. Добавить unit тесты для всех стратегий
2. Улучшить визуализацию ветвления
3. Добавить поддержку RAG (Retrieval Augmented Generation)
4. Реализовать summary-based стратегию
5. Добавить возможность ручного редактирования фактов
6. Оптимизировать производительность для больших диалогов
