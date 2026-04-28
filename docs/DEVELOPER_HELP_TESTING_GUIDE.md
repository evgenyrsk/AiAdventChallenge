# Developer Help Testing Guide

## 1. Подготовка
- Перейдите в корень проекта:
```bash
cd /Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge
```
- Запустите все MCP server'ы, включая document index server:
```bash
./gradlew :mcp-server:runMultiServer
```
- В отдельном терминале соберите и установите Android-приложение:
```bash
./gradlew :app:installDebug
```
- Убедитесь, что проект открыт как git-репозиторий:
```bash
git branch --show-current
```

## 2. Индексация `project_docs`
- Выполните индексацию корня проекта в `project_docs`:
```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"index_documents",
    "params":{
      "path":"/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge",
      "source":"project_docs",
      "strategies":["fixed_size","structure_aware"]
    }
  }'
```
- Проверьте статистику индекса:
```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":2,
    "method":"get_index_stats",
    "params":{"source":"project_docs"}
  }'
```
- После индексации проверьте, что `README.md` и файлы из `docs/` попали в индекс.

## 3. Проверка MCP tools
- Проверьте `get_git_branch`:
```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":3,
    "method":"get_git_branch",
    "params":{}
  }'
```
- Проверьте `list_project_files`:
```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":4,
    "method":"list_project_files",
    "params":{"limit":20}
  }'
```
- Проверьте `get_git_diff_summary`:
```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":5,
    "method":"get_git_diff_summary",
    "params":{"maxChars":2000}
  }'
```
- Ожидаемо:
  - `get_git_branch` возвращает текущую ветку
  - `list_project_files` не включает `build/`, `.git/`, `.gradle/`, `.idea/`
  - `get_git_diff_summary` возвращает короткий status и diff summary

## 4. Проверка `/help`
- Запустите приложение на устройстве или эмуляторе.
- Отправьте `/help`.
- Ожидаемо: в чате появляется краткая справка по developer assistant.

- Отправьте `/help какая текущая git-ветка?`
- Ожидаемо: ответ содержит `Current branch:` и использует MCP context.

- Отправьте `/help как устроен RAG pipeline?`
- Ожидаемо: ответ опирается на `project_docs`, показывает sources и не выдумывает отсутствующие детали.

- Отправьте `/help где описана архитектура проекта?`
- Ожидаемо: ответ ссылается на релевантные `README/docs` файлы.

## 5. Проверка, что fitness flow не сломан
- Отправьте обычный запрос без команды, например: `Составь тренировку на грудь`.
- Ожидаемо: сообщение идет в обычный fitness flow.
- После этого убедитесь, что ответ не ссылается на developer docs и git context.

## 6. Проверка изоляции контекста
- Сначала отправьте 1-2 вопроса через `/help`.
- Затем отправьте обычный fitness-запрос.
- Ожидаемо: developer history не влияет на fitness-ответ.

## 7. Проверка graceful degradation
- Временно не индексируйте `project_docs` или остановите MCP server:
```bash
pkill -f "DocumentIndexServerMainKt|MultiServerLauncherKt"
```
- Затем снова запустите только приложение или поднимите сервер без новой индексации.
- Отправьте `/help как устроен RAG pipeline?`
- Ожидаемо: чат не падает, а честно сообщает, что project docs retrieval недоступен или контекста недостаточно.
