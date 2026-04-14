# Weekly Fitness Summary - руководство по сборке и запуску

## Требования

- JDK 11+
- Gradle 7+ (опционально)
- Kotlin 1.9+
- Maven Central доступен для зависимостей

## Способы сборки

### Способ 1: Через Gradle (рекомендуется)

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/mcp-server

# Если у вас есть gradlew (gradle wrapper)
./gradlew build

# Если нет gradlew, используйте установленный gradle
gradle build

# Запуск после сборки
./gradlew run
# или
gradle run
```

### Способ 2: Через Android Studio

1. Откройте проект в Android Studio
2. Откройте модуль `mcp-server`
3. Нажмите на `Main.kt`
4. Нажмите ▶️ (Run) рядом с `fun main()`

### Способ 3: Через IntelliJ IDEA

1. Откройте проект в IntelliJ IDEA
2. Откройте файл `mcp-server/src/main/kotlin/com/example/mcp/server/Main.kt`
3. Нажмите ▶️ (Run 'MainKt')

### Способ 4: Ручная компиляция через kotlinc

Если у вас установлен Kotlin Compiler:

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/mcp-server

# Создать директорию для классов
mkdir -p build/classes

# Найти зависимости (вам нужно скачать их вручную или использовать Maven)
# Для примера:
# https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/1.7.3/kotlinx-serialization-json-1.7.3.jar
# https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.7.3/kotlinx-coroutines-core-1.7.3.jar
# https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar

# Компиляция
kotlinc \
  -d build/classes \
  -cp "kotlinx-serialization-json-1.7.3.jar:kotlinx-coroutines-core-1.7.3.jar:sqlite-jdbc-3.45.1.0.jar" \
  src/main/kotlin/com/example/mcp/server/**/*.kt

# Запуск
kotlin -cp "build/classes:kotlinx-serialization-json-1.7.3.jar:kotlinx-coroutines-core-1.7.3.jar:sqlite-jdbc-3.45.1.0.jar" com.example.mcp.server.MainKt
```

## Быстрый старт, если Gradle доступен

```bash
# 1. Переход в директорию mcp-server
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/mcp-server

# 2. Сборка проекта
gradle build

# 3. Запуск сервера
gradle run
```

После запуска вы увидите:
```
🚀 Starting MCP Server on http://10.0.2.2:8080
✅ MCP Server is running on http://10.0.2.2:8080
📡 Android Emulator should use: http://10.0.2.2:8080
🖥️  For testing from host: http://localhost:8080
📅 Starting BackgroundSummaryScheduler (interval: 1min)
```

## Тестирование

В новом терминале:

```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
./test_fitness_mcp.sh
```

Или вручную:

```bash
# Проверить здоровье сервера
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 1, "method": "ping"}'

# Получить список tools
curl -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": 2, "method": "tools/list"}'
```

## Устранение проблем

### Проблема: "command not found: gradle"

**Решение 1:** Установить Gradle через SDKMAN
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.5
```

**Решение 2:** Использовать Android Studio или IntelliJ IDEA (они имеют встроенную поддержку Gradle)

### Проблема: "kotlinx.serialization not found"

**Решение:** Убедитесь, что `kotlin("plugin.serialization")` подключён в build.gradle.kts

### Проблема: "SQLite driver not found"

**Решение:** Проверьте, что зависимость `org.xerial:sqlite-jdbc:3.45.1.0` добавлена в build.gradle.kts

### Проблема: База данных не создаётся

**Решение:** Проверьте права на запись в текущей директории. База данных создаётся в файле `./fitness_data.db`

## Проверка структуры проекта

```bash
# Проверить наличие всех файлов
find /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/mcp-server/src/main/kotlin -name "*.kt" | sort

# Ожидаемый вывод:
# data/fitness/FitnessDatabase.kt
# data/fitness/FitnessLogDao.kt
# data/fitness/FitnessRepository.kt
# data/fitness/ScheduledSummaryDao.kt
# handler/McpJsonRpcHandler.kt
# Main.kt
# model/fitness/FitnessLog.kt
# model/fitness/FitnessSummary.kt
# model/fitness/ScheduledSummary.kt
# model/JsonRpcModels.kt
# scheduler/BackgroundSummaryScheduler.kt
# service/fitness/FitnessSummaryService.kt
```

## Интеграция с Android

1. Запустите MCP сервер (см. выше)
2. В Android-приложении:
   - Настройте URL сервера: `http://10.0.2.2:8080` (для эмулятора)
   - Или `http://localhost:8080` (для устройства на том же хосте)
3. Используйте `CallMcpToolUseCase` для вызова инструментов:
   - `add_fitness_log`
   - `get_fitness_summary`
   - `run_scheduled_summary`
   - `get_latest_scheduled_summary`

## Дополнительные ресурсы

- [Kotlin Serialization Documentation](https://kotlinlang.org/docs/serialization.html)
- [SQLite JDBC Documentation](https://github.com/xerial/sqlite-jdbc)
- [Kotlin Coroutines Documentation](https://kotlinlang.org/docs/coroutines-overview.html)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)

## Поддержка

Если возникли проблемы:
1. Проверьте версию JDK: `java -version` (должна быть 11+)
2. Проверьте версию Kotlin: `kotlin -version` (должна быть 1.9+)
3. Проверьте наличие зависимостей в build.gradle.kts
4. Проверьте структуру файлов проекта
5. Посмотрите логи ошибок в консоли

---

Удачи с запуском! 🚀
