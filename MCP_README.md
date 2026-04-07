# MCP Integration - Инструкция по запуску

## Что реализовано

Минимальная интеграция MCP (Model Context Protocol) в Android приложение:

### MCP Server (локальный JVM)
- Путь: `mcp-server/`
- Простой HTTP сервер на встроенном `com.sun.net.httpserver`
- JSON-RPC 2.0 для MCP протокола
- **2 тестовых инструмента:**
  - `ping` - возвращает "pong"
  - `get_app_info` - возвращает информацию о приложении

### Android MCP Client
- Путь: `app/src/main/java/com/example/aiadventchallenge/...`
- **Слои:**
  - `data/mcp/` - `McpRepository`, `McpJsonRpcClient`, модели
  - `domain/model/mcp/` - модели данных
  - `domain/usecase/mcp/` - `GetMcpToolsUseCase`
  - `ui/screens/mcp/` - `McpDebugViewModel`, `McpDebugSheet`

### UI для проверки
- Кнопка **🔧 MCP** в Toolbar экрана чата (рядом с Info)
- BottomSheet с результатами:
  - Статус подключения
  - Список полученных инструментов
  - Ошибки (если есть)
  - Кнопка "Повторить" при ошибке

---

## Запуск MCP Server

### Шаг 1: Запуск сервера

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
./gradlew :mcp-server:run
```

**Ожидаемый вывод:**
```
🚀 Starting MCP Server on http://10.0.2.2:8080
✅ MCP Server is running on http://10.0.2.2:8080
📡 Android Emulator should use: http://10.0.2.2:8080
🖥️  For testing from host: http://localhost:8080
```

**Важно:**
- Сервер работает на порту 8080
- Для Android Emulator используется адрес `10.0.2.2:8080` (localhost из эмулятора)
- Для тестирования с хоста используйте `http://localhost:8080`

---

## Запуск Android приложения

### Шаг 2: Запуск приложения

1. Откройте проект в Android Studio
2. Запустите эмулятор Android
3. Соберите и запустите приложение:
   - Нажмите кнопку Run ▶️
   - Или используйте: `./gradlew :app:installDebug`

### Шаг 3: Проверка MCP интеграции

1. Откройте экран чата в приложении
2. Нажмите кнопку **🔧 MCP** в Toolbar (справа вверху)
3. Откроется BottomSheet с результатами

**Ожидаемый результат:**

#### ✅ Успешное подключение:
```
🔧 MCP Debug

✅ Подключено к MCP серверу

📦 Доступные инструменты (2):

🔧 ping
Simple ping tool to test MCP connection. Returns 'pong' message.

🔧 get_app_info
Returns information about the application including version, platform, and build details.

🔗 URL: http://10.0.2.2:8080
```

#### ❌ Ошибка подключения:
```
🔧 MCP Debug

❌ Ошибка подключения
Connection refused: Connection refused

[Повторить]
```

---

## Логи

### Android Logcat

Фильтр по тегу: `McpDebugViewModel`

**Успешное подключение:**
```
D/McpDebugViewModel: 🔍 Checking MCP connection...
D/McpJsonRpcClient: 📤 Sending MCP Request: {"jsonrpc":"2.0","id":1,"method":"initialize"}
D/McpJsonRpcClient: 📥 MCP Response: {"jsonrpc":"2.0","id":1,"result":{"message":"MCP Server initialized successfully"}}
D/McpRepository: ✅ Initialized: MCP Server initialized successfully
D/McpJsonRpcClient: 📤 Sending MCP Request: {"jsonrpc":"2.0","id":2,"method":"tools/list"}
D/McpJsonRpcClient: 📥 MCP Response: {"jsonrpc":"2.0","id":2","result":{"tools":[...]}}
D/McpRepository: 📦 Received 2 tools
D/McpRepository:    - ping: Simple ping tool to test MCP connection.
D/McpRepository:    - get_app_info: Returns information about the application.
D/McpDebugViewModel: ✅ MCP connected successfully
D/McpDebugViewModel: 📦 Tools received: 2
```

### MCP Server Console

```
📨 Request: {"jsonrpc":"2.0","id":1,"method":"initialize"}
   Method: initialize
📤 Response: {"jsonrpc":"2.0","id":1","result":{"message":"MCP Server initialized successfully"}}

📨 Request: {"jsonrpc":"2.0","id":2,"method":"tools/list"}
   Method: tools/list
   Returning 2 tools
📤 Response: {"jsonrpc":"2.0","id":2","result":{"tools":[...]}}
```

---

## Структура файлов

### MCP Server
```
mcp-server/
├── build.gradle.kts
└── src/main/kotlin/com/example/mcp/server/
    ├── Main.kt
    ├── model/JsonRpcModels.kt
    └── handler/McpJsonRpcHandler.kt
```

### Android Client
```
app/src/main/java/com/example/aiadventchallenge/
├── data/mcp/
│   ├── McpRepository.kt
│   ├── McpJsonRpcClient.kt
│   └── model/McpJsonRpcModels.kt
├── domain/model/mcp/
│   └── McpModels.kt
├── domain/usecase/mcp/
│   └── GetMcpToolsUseCase.kt
├── ui/screens/mcp/
│   ├── McpDebugViewModel.kt
│   ├── McpDebugViewModelFactory.kt
│   └── McpDebugSheet.kt
└── di/AppDependencies.kt (обновлен)
```

---

## Troubleshooting

### ❌ Connection refused

**Проблема:** MCP сервер не запущен или порт занят

**Решение:**
1. Проверьте, что MCP сервер запущен: `./gradlew :mcp-server:run`
2. Проверьте, что порт 8080 свободен: `lsof -i :8080`
3. Проверьте адрес: должно быть `http://10.0.2.2:8080` для эмулятора

### ❌ Connection timeout

**Проблема:** Эмулятор не может достичь сервера

**Решение:**
1. Проверьте настройки сети эмулятора
2. Проверьте firewall на хосте
3. Попробуйте ping из эмулятора: `adb shell ping 10.0.2.2`

### ✅ Подключено, но список инструментов пуст

**Проблема:** Сервер вернул пустой список

**Решение:**
1. Проверьте логи MCP сервера
2. Проверьте, что метод `tools/list` реализован в `McpJsonRpcHandler.kt`

---

## Следующие шаги

На данном этапе MCP интегрирован минимально:
- ✅ Установлено соединение с MCP сервером
- ✅ Получен список инструментов
- ✅ Отображен результат в UI

**Чего НЕ реализовано (по ТЗ):**
- ❌ Tool calling в пайплайн LLM-чата
- ❌ Использование инструментов в ответах модели
- ❌ Интеграция с контекстом и памятью чата

Эти шаги могут быть реализованы в следующих задачах.

---

## Краткая сводка

**Что сделано:**
1. ✅ Создан локальный MCP сервер с 2 инструментами
2. ✅ Создан Android MCP клиент (JSON-RPC 2.0 + OkHttp)
3. ✅ Добавлен UI для проверки (кнопка + BottomSheet)
4. ✅ Интегрировано в DI (AppDependencies)

**Как проверить:**
1. Запустите MCP сервер: `./gradlew :mcp-server:run`
2. Запустите Android приложение
3. Откройте чат
4. Нажмите кнопку 🔧 MCP
5. Убедитесь, что отображается список из 2 инструментов

**Успех:** Если видите инструменты `ping` и `get_app_info` — интеграция работает! 🎉
