# Инструкция по интеграционному тестированию MCP фитнес-сценария

## Дата
2026-04-13

## Статус
✅ MCP сервер запущен и работает
✅ Android приложение собирается успешно
✅ Все P0 задачи выполнены

---

## Текущее состояние

### MCP Сервер
- **URL:** `http://10.0.2.2:8080` (для Android Emulator)
- **URL:** `http://localhost:8080` (для тестирования с хоста)
- **Статус:** ✅ Работает
- **PID:** 4717

### Android приложение
- **Сборка:** ✅ BUILD SUCCESSFUL
- **P0 задачи:** ✅ Выполнены
  - Автоматическая детекция фитнес-сценария
  - Инициализация соединения с MCP сервером
  - Индикатор соединения MCP в UI
  - Обновление тестов

---

## Тестирование

### Шаг 1: Запуск MCP сервера

**Команда:**
```bash
./gradlew :mcp-server:run
```

**Ожидаемый результат:**
```
✅ MCP Server is running on http://10.0.2.2:8080
📡 Android Emulator should use: http://10.0.2.2:8080
🖥️  For testing from host: http://localhost:8080
```

**Проверка:**
```bash
curl http://localhost:8080
# Ожидаемый ответ: ошибка JSON-RPC (сервер работает, но нужен правильный запрос)
```

---

### Шаг 2: Запуск Android приложения

**Команда:**
```bash
# Через Android Studio
# Или через командную строку:
./gradlew installDebug
```

**Или через Android Studio:**
1. Откройте проект в Android Studio
2. Выберите эмулятор (нажмите Run или Shift+F10)
3. Дождитесь запуска приложения

---

### Шаг 3: Проверка индикатора соединения MCP

**Что проверять:**
1. В правом верхнем углу экрана (TopAppBar) есть иконка ☁️/☁️ off
2. **Connected** = зелёная иконка ☁️
3. **Disconnected** = красная иконка ☁️ off

**Ожидаемый результат:**
- Иконка должна быть зелёной ☁️ (Connected)
- Если красная - проверить что MCP сервер запущен и URL правильный

---

### Шаг 4: Тестирование автоматической детекции фитнес-сценария

**Что проверять:**
1. Откройте чат
2. Отправьте запрос с фитнес-ключевыми словами

**Примеры запросов:**
```
1. "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"
2. "Покажи мои фитнес логи за месяц"
3. "Какие тренировки были на прошлой неделе?"
4. "Составь сводку моих тренировок"
5. "Напомни мне про тренировку завтра"
```

**Ожидаемый результат:**
- В Logcat должно быть:
```
🔍 Checking for MCP tool in LLM response...
✅ Detected fitness flow request
🔧 Calling MCP tool: execute_multi_server_flow
🎯 Executing multi-server flow for: <запрос>
```

- Выполняется 3 шага:
  1. ✅ fitness-server-1 → search_fitness_logs
  2. ✅ fitness-server-1 → summarize_fitness_logs
  3. ✅ reminder-server-1 → create_reminder_from_summary

---

### Шаг 5: Проверка контекста в ответе LLM

**Что проверять:**
1. После выполнения фитнес-сценария в чате должен появиться ответ LLM
2. Ответ должен содержать MCP контекст (результат выполнения flow)

**Ожидаемый формат ответа:**
```
🏋️ FITNESS MCP FLOW - ВЫПОЛНЕНИЕ СЦЕНАРИЯ
================================================================================

Flow: fitness_summary_to_reminder
Статус: ✅ Успешно
Шагов выполнено: 3/3
Длительность: <X>ms

Шаги выполнения:
✅ fitness-server-1 → search_fitness_logs (<X>ms)
✅ fitness-server-1 → summarize_fitness_logs (<X>ms)
✅ reminder-server-1 → create_reminder_from_summary (<X>ms)

================================================================================
```

---

### Шаг 6: Тестирование стратегий контекста

#### SlidingWindowStrategy

**Шаги:**
1. В настройках выберите "Sliding Window"
2. Установите windowSize = 10
3. Отправьте 20 сообщений
4. Проверьте логи

**Ожидаемый результат:**
```
📊 SlidingWindow context:
  Total messages: 20
  Window size: 10
  Messages in context: 10
  Messages filtered: 10
```

#### StickyFactsStrategy

**Шаги:**
1. В настройках выберите "Sticky Facts"
2. Отправьте: "Меня зовут Алексей, я хочу набрать массу, вес 85 кг"
3. Отправьте несколько сообщений
4. Проверьте что факты сохраняются в контексте

**Ожидаемый результат:**
```
📝 StickyFacts context:
  Total messages: 5
  Window size: 10
  Messages in context: 10
  Messages filtered: 0

Known facts about conversation:
user_name: Алексей
user_goal: Набор массы
user_weight: 85
```

#### BranchingStrategy

**Шаги:**
1. В настройках выберите "Branching"
2. Отправьте 5 сообщений
3. Долгое нажмите на сообщение #3 → "Создать ветку"
4. Введите название ветки: "Вариант 1"
5. Отправьте сообщение в новой ветке
6. Нажмите на название ветки вверху → выберите "Main"
7. Проверьте что контекст переключается

**Ожидаемый результат:**
```
📊 Branching context:
  Active branch: main
  Total messages in DB: 5
  Messages in active path: 5

📊 Branching context:
  Active branch: Вариант 1
  Total messages in DB: 8
  Messages in active path: 3
```

---

## Troubleshooting

### Проблема: Индикатор MCP соединения красный ☁️ off

**Причины:**
1. MCP сервер не запущен
2. Неверный URL в `AppDependencies.kt`
3. Эмулятор не может подключиться к localhost

**Решение:**
```bash
# 1. Проверить что MCP сервер запущен
ps aux | grep "mcp-server"

# 2. Проверить логи MCP сервера
tail -f /tmp/mcp-server.log

# 3. Проверить URL в AppDependencies.kt
# Должно быть: "http://10.0.2.2:8080"
```

### Проблема: Фитнес-сценарий не запускается автоматически

**Причины:**
1. Ключевые слова не совпадают
2. `detectAndExecuteTool()` возвращает `NoToolFound`

**Решение:**
```bash
# Проверить логи в Logcat
adb logcat -s McpToolOrchestrator

# Ожидаемый лог:
# 🔍 Checking for MCP tool in LLM response...
# ✅ Detected fitness flow request (если фитнес-запрос)
# ⚠️ No tool found (если обычный запрос)
```

### Проблема: Multi-server flow падает с ошибкой

**Причины:**
1. Fitness или Reminder сервер недоступен
2. База данных SQLite заблокирована

**Решение:**
```bash
# Проверить логи MCP сервера
tail -f /tmp/mcp-server.log

# Ожидаемые логи:
# 🚀 Executing flow: fitness_summary_to_reminder
# ⏭️  Executing step: search_logs
# ✅ Step search_logs completed successfully
# ...
```

---

## Мониторинг

### Логи Android приложения

**Команда:**
```bash
adb logcat | grep -E "McpToolOrchestrator|ChatViewModel|McpRepository"
```

**Важные теги:**
- `McpToolOrchestrator` - детекция и выполнение MCP инструментов
- `ChatViewModel` - обработка сообщений и UI состояние
- `McpRepository` - соединение с MCP сервером

### Логи MCP сервера

**Файл:** `/tmp/mcp-server.log`

**Команда:**
```bash
tail -f /tmp/mcp-server.log
```

**Важные сообщения:**
```
✅ MCP Server is running on http://10.0.2.2:8080
🚀 Executing flow: fitness_summary_to_reminder
✅ Flow fitness_summary_to_reminder completed successfully!
```

---

## Критерии успеха

### Основные (P0)
- [x] MCP сервер запущен и работает
- [x] Android приложение собирается
- [ ] Индикатор соединения MCP зелёный при запуске
- [ ] Фитнес-запросы автоматически детектируются
- [ ] Multi-server flow выполняется успешно (3 шага)
- [ ] Ответ LLM содержит MCP контекст

### Дополнительные (P1)
- [ ] Логи выполнения flow отображаются в UI
- [ ] Ошибки детекции показываются в чате
- [ ] SlidingWindowStrategy работает корректно
- [ ] StickyFactsStrategy сохраняет факты
- [ ] BranchingStrategy переключает ветки

---

## Следующие шаги

### Ожидает выполнения (P1)
1. 📋 Добавить логи выполнения flow в UI
2. 📋 Добавить обработку ошибок детекции
3. 📋 Полное интеграционное тестирование всех стратегий

---

## Ресурсы

- План реализации: `.opencode/plans/mcp-implementation-plan.md`
- Отчёт P0: `.opencode/plans/mcp-p0-implementation-report.md`
- MCP документация: `MCP_INTEGRATION.md`
- Стратегии контекста: `CONTEXT_STRATEGIES.md`
