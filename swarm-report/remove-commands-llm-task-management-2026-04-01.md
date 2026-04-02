# Отчет: Удаление системных команд и переход на LLM-управление задачами

## Дата
2026-04-01

## Краткое описание задачи
Удалить всю логику, связанную с системными командами (/task, /help и прочими). Все управление задачами должно строиться исключительно на взаимодействии с LLM по строгому контракту и правилам конечного автомата.

---

## Итоги выполнения

### Что было удалено

**1. Удаленные файлы (3 файла):**
- ✅ `domain/model/TaskCommand.kt` - модель системных команд
- ✅ `ui/screens/chat/components/CommandAutocomplete.kt` - UI автодополнения команд
- ✅ `ui/screens/chat/components/HelpSheet.kt` - UI справки по командам

**2. Очищенные файлы:**

#### `ui/screens/chat/ChatUiState.kt`
- ❌ Удалено поле: `showCommandAutocomplete: Boolean`
- ❌ Удалено поле: `commandQuery: String`
- ❌ Удалено поле: `showHelpSheet: Boolean`

#### `ui/screens/chat/ChatViewModel.kt`
- ❌ Удален импорт: `TaskCommand`
- ❌ Удален класс: `ParsedCommand`
- ❌ Удалены методы:
  - `showCommandAutocomplete(query: String)`
  - `hideCommandAutocomplete()`
  - `showHelpSheet()`
  - `hideHelpSheet()`
  - `getFilteredCommands(query: String)`
  - `parseCommands(input: String)`
  - `handleCommand(command: String, content: String)`
  - `addSystemMessage(message: String)`
- ❌ Удален companion object: `COMMANDS`
- ✅ Обновлен метод: `sendMessage()` - убрана логика парсинга команд

#### `ui/screens/chat/ChatScreen.kt`
- ❌ Удалена переменная: `filteredCommands`
- ❌ Удален импорт: `Icons.AutoMirrored.Filled.Help`
- ❌ Удалены импорты:
  - `CommandAutocomplete`
  - `HelpSheet`
- ✅ Обновлен TextField:
  - Убрана логика `hideCommandAutocomplete()` в `onValueChange`
  - Изменен placeholder с "Сообщение или /help" на "Сообщение"
- ❌ Удалена IconButton справки (иконка ❓)
- ❌ Удалены модальные окна:
  - `showCommandAutocomplete` блок
  - `showHelpSheet` блок

#### `data/agent/ChatAgent.kt`
- ✅ Обновлен метод `buildRequestConfigWithTask()`:
  - Убраны ссылки на `Prompts.COMMANDS_SYSTEM_PROMPT`
  - Упрощена логика формирования промптов

#### `data/config/Prompts.kt`
- ❌ Удален константа: `COMMANDS_SYSTEM_PROMPT`
- ❌ Удалено описание всех команд (/task, /continue, /done, /cancel, /help)

---

## Итоговое состояние

### Что осталось (функциональность управления задачами)

✅ **LLM-управление задачами:**
- `TaskIntent` enum (NEW_TASK, CONTINUE_TASK, SWITCH_TASK, PAUSE_TASK, CLARIFICATION)
- `EnhancedTaskAiResponse` data class для расширенных ответов AI
- Фазо-специфичные промпты в `TaskPromptBuilder`
- `processIntelligentMessage()` - умная обработка сообщений
- `handleTaskIntent()` - обработка намерений от LLM
- Логика создания задач через LLM (без системных команд)

### Как теперь работает управление задачами

**Сценарий 1: Создание новой задачи**
```
Пользователь: "Составь программу тренировок"
↓
LLM определяет: task_intent: NEW_TASK
↓
ChatViewModel: handleTaskIntent() → createTask("Составить программу тренировок")
↓
Задача создается в статусе RESEARCH
```

**Сценарий 2: Переключение задач**
```
Пользователь: "А теперь протокол питания"
↓
LLM определяет: task_intent: SWITCH_TASK, new_task_query: "Составить протокол питания"
↓
ChatViewModel: pauseTask() → createTask("Составить протокол питания")
↓
Старая задача приостанавливается, новая создается
```

**Сценарий 3: Приостановка задачи**
```
Пользователь: "Я подумаю об этом позже"
↓
LLM определяет: task_intent: PAUSE_TASK
↓
ChatViewModel: pauseTask()
↓
Задача приостанавливается
```

**Сценарий 4: Продолжение задачи**
```
Пользователь: "Я новичок без опыта"
↓
LLM определяет: task_intent: CONTINUE_TASK, step_completed: true, transition_to: PLANNING
↓
ChatViewModel: advanceTask() → transitionTaskTo(PLANNING)
↓
Задача переходит на следующий этап
```

---

## Результаты Validation

✅ **Компиляция**: Успешно
```
./gradlew compileDebugKotlin
BUILD SUCCESSFUL
```

✅ **Проверка отсутствующих ссылок**:
- Нет ссылок на `TaskCommand`
- Нет ссылок на `ParsedCommand`
- Нет ссылок на `COMMANDS`
- Нет ссылок на `showCommandAutocomplete`
- Нет ссылок на `commandQuery`
- Нет ссылок на `showHelpSheet`
- Нет ссылок на `getFilteredCommands`
- Нет ссылок на `parseCommands`
- Нет ссылок на `handleCommand`
- Нет ссылок на `addSystemMessage`

✅ **Логика задач полностью перенесена на LLM**:
- LLM автоматически определяет намерения пользователя
- Задачи создаются через `handleTaskIntent()`
- Фазо-специфичные промпты содержат все правила конечного автомата

---

## Проблемы и откаты

**Нет**. Все изменения прошли успешно без откатов.

---

## Преимущества нового подхода

### 1. Естественный интерфейс
- Пользователь общается с LLM на естественном языке
- Не нужно запоминать системные команды
- LLM сама понимает намерения пользователя

### 2. Гибкость
- LLM может адаптироваться к разным формулировкам запросов
- Нет жесткого набора команд
- Можно расширять логику через промпты

### 3. Упрощение кода
- Удалено ~300 строк кода (модели, UI, логика парсинга)
- Меньше мест для ошибок
- Легче поддерживать

### 4. Соответствие требованиям
- Вся логика управления задачами через LLM
- Строгий контракт и правила
- Конечный автомат соблюдается

---

## Статус
**Done** ✅

Все системные команды полностью удалены:
- Удалены 3 файла (TaskCommand, CommandAutocomplete, HelpSheet)
- Очищены 5 файлов (ChatUiState, ChatViewModel, ChatScreen, ChatAgent, Prompts)
- Код успешно компилируется
- Управление задачами полностью работает через LLM
- Фазо-специфичные промпты обеспечивают соблюдение правил конечного автомата
