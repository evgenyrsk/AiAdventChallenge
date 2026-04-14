# AI Fitness Assistant

Android-приложение — AI-помощник по фитнесу, питанию и здоровому образу жизни с двумя экранами и разными режимами ответов.

---

## Возможности

- **Два экрана** — "Чат" и "Сравнение" с переключением через навигацию
- **Два режима ответов** — "Без ограничений" и "С ограничениями"
- **Четыре режима промптов** — "Прямой ответ", "Пошагово", "Meta-prompt", "Эксперты"
- **Персонализация** — учёт возраста, веса, роста, цели и уровня активности
- **Тематический фокус** — вежливый отказ на вопросы не по теме ЗОЖ
- **Дружелюбный тон** — тёплые и поддерживающие ответы

---

## Режимы работы

### Без ограничений (по умолчанию)
- Развёрнутые ответы с примерами и рекомендациями
- Свободный формат без технических лимитов
- Полезные советы и объяснения

### С ограничениями
- **Одно предложение** — краткий и точный ответ
- **maxTokens=60** — техническое ограничение длины
- **stop=["END"]** — маркер завершения ответа

Идеально для быстрого получения сути.

---

## Режимы промптов (экран "Сравнение")

### Прямой ответ
- Базовый режим без дополнительных инструкций
- Прямой ответ от AI-ассистента
- Подходит для простых вопросов

### Пошагово
- AI решает задачу пошагово
- Детальное объяснение каждого этапа
- Позволяет отследить ход рассуждений

### Meta-prompt
- AI сначала составляет оптимальный промпт
- Затем использует его для ответа
- Максимальная точность через саморефлексию

### Эксперты
- Групповой ответ от трёх экспертов
- Нутрициолог, фитнес-тренер, критик
- Многогранный взгляд на проблему

---

## Профиль пользователя

Опциональные параметры для персонализации ответов:

| Параметр | Описание |
|----------|----------|
| Возраст | В годах |
| Вес | В килограммах |
| Рост | В сантиметрах |
| Цель | Похудение / Набор мышц / Поддержание формы / Улучшение здоровья |
| Активность | Сидячий / Лёгкий / Умеренный / Активный / Очень активный |

---

## Навигация

Приложение использует NavigationBar для переключения между двумя экранами:

| Экран | Описание |
|-------|----------|
| Чат | Основной экран общения с AI-ассистентом |
| Сравнение | Сравнение ответов в разных режимах промптов |

Переключение осуществляется через нижнюю навигационную панель.

---

## Архитектура

Clean Architecture с разделением на слои:

```
data → domain → ui
```

### Структура проекта

```
app/src/main/java/com/example/aiadventchallenge/
├── data/
│   ├── api/           # HTTP клиент, конфигурация API
│   ├── model/         # DTO (ChatRequest, ChatResponse, Message)
│   ├── parser/        # Парсинг ответов
│   ├── repository/    # Реализация репозитория
│   └── Prompts.kt     # Системные промпты
├── domain/
│   ├── model/         # Бизнес-модели (Answer, ChatResult, PromptMode, UserProfile)
│   ├── repository/    # Интерфейсы репозиториев
│   └── usecase/       # UseCase (AskAiUseCase, AskWithPromptModeUseCase)
├── di/                # Внедрение зависимостей
├── ui/
│   ├── components/    # UI-компоненты (AnswerDisplay, MessageInput, ModeSelector и др.)
│   ├── promptcomparison/ # Экран "Сравнение" (PromptComparisonScreen, ViewModel)
│   ├── theme/         # Тема оформления (colors, type, theme)
│   ├── MainActivity   # Точка входа, навигация между экранами
│   ├── ChatScreen     # Основной экран чата
│   └── MainViewModel  # Управление состоянием чата
```

---

## Document Indexing

В проекте есть локальный document indexing / retrieval feature для MCP server:

- локальная индексация markdown, text, code и PDF
- две стратегии chunking: `fixed_size` и `structure_aware`
- локальный SQLite index + JSON export
- MCP tools для index/stats/compare/search/retrieve

Документация по feature:

- [Feature Overview](docs/DOCUMENT_INDEXING_FEATURE.md)
- [Demo Corpus](demo/document-indexing-corpus/README.md)
- [Fitness Demo Corpus](demo/fitness-knowledge-corpus/README.md)
- [Fitness Demo Script](docs/FITNESS_DEMO_SCRIPT.md)
- [Fitness Video Demo](docs/FITNESS_VIDEO_DEMO.md)
- [Demo Flow](docs/DOCUMENT_INDEXING_DEMO.md)
- [Result Demo](docs/DOCUMENT_INDEXING_RESULT_DEMO.md)
- [Acceptance](docs/DOCUMENT_INDEXING_ACCEPTANCE.md)

---

## Технологии

- **Kotlin** — основной язык
- **Jetpack Compose** — декларативный UI
- **Material3** — современная система дизайна
- **OkHttp** — HTTP-клиент
- **Kotlinx Serialization** — JSON сериализация
- **Kotlin Coroutines** — асинхронность
- **StateFlow** — управление состоянием

---

## UI Компоненты

### Переиспользуемые компоненты
- **AnswerDisplay** — отображение ответа от AI
- **LoadingIndicator** — индикатор загрузки
- **MessageInput** — поле ввода сообщений
- **ModeSelector** — выбор режима ответов (Без ограничений / С ограничениями)
- **PromptModeSelector** — выбор режима промптов (4 режима в экране Сравнение)
- **UserProfileInput** — ввод параметров профиля пользователя

### Экраны
- **ChatScreen** — основной экран чата с двумя режимами ответов
- **PromptComparisonScreen** — экран сравнения промптов с четырьмя режимами

---

## Запуск проекта

### 1. Клонировать репозиторий

```bash
git clone https://github.com/evgenyrsk/AiAdventChallenge.git
```

### 2. Открыть в Android Studio

```
File → Open → выбрать папку проекта
```

### 3. Добавить API ключ

В файле `local.properties`:

```properties
AI_API_KEY=your_openrouter_api_key
```

### 4. Запустить

На эмуляторе или физическом устройстве.

---

## Пример запроса к API

### Без ограничений

```json
{
  "model": "nvidia/nemotron-3-super-120b-a12b:free",
  "messages": [
    {"role": "system", "content": "Ты — дружелюбный помощник по фитнесу..."},
    {"role": "user", "content": "Как правильно питаться?"}
  ]
}
```

### С ограничениями

```json
{
  "model": "nvidia/nemotron-3-super-120b-a12b:free",
  "messages": [
    {"role": "system", "content": "Ты — дружелюбный помощник по фитнесу..."},
    {"role": "user", "content": "Как правильно питаться?"}
  ],
  "max_tokens": 60,
  "stop": ["END"],
  "reasoning": {"effort": "none", "exclude": true}
}
```

### Режимы промптов

#### Прямой ответ

```json
{
  "model": "nvidia/nemotron-3-super-120b-a12b:free",
  "messages": [
    {"role": "system", "content": "Ты — дружелюбный помощник по фитнесу..."},
    {"role": "user", "content": "Как правильно питаться?"}
  ]
}
```

#### Пошагово

```json
{
  "model": "nvidia/nemotron-3-super-120b-a12b:free",
  "messages": [
    {"role": "system", "content": "Ты — дружелюбный помощник по фитнесу. Решай задачи пошагово."},
    {"role": "user", "content": "Как правильно питаться?"}
  ]
}
```

#### Meta-prompt

```json
{
  "model": "nvidia/nemotron-3-super-120b-a12b:free",
  "messages": [
    {"role": "system", "content": "Ты — дружелюбный помощник по фитнесу. Сначала составь оптимальный промпт для ответа, затем используй его."},
    {"role": "user", "content": "Как правильно питаться?"}
  ]
}
```

#### Эксперты

```json
{
  "model": "nvidia/nemotron-3-super-120b-a12b:free",
  "messages": [
    {"role": "system", "content": "Ты — группа экспертов: нутрициолог, фитнес-тренер и критик. Каждый эксперт даёт свой взгляд на проблему."},
    {"role": "user", "content": "Как правильно питаться?"}
  ]
}
```
