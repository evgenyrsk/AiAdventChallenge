# Support Assistant

Отдельный мини-сервис поддержки пользователей для продукта “фитнес-ассистент”.

Сервис не встроен в Android chat flow и не использует fitness memory/context. Он читает отдельные support FAQ/docs, получает mock CRM context через локальные MCP-style tools над JSON и вызывает настраиваемый LLM backend.

## Возможности

- `GET /health`
- `POST /support/ask`
- mock CRM data: `data/users.json`, `data/tickets.json`
- support RAG: `data/faq.json`
- prompt template: `prompts/support_assistant_prompt.md`
- debug endpoints:
  - `GET /support/users/{id}`
  - `GET /support/tickets/{id}`

## Запуск

```bash
./gradlew :support-assistant:run
```

По умолчанию сервис слушает `http://localhost:8091`.

Gradle application task может запускать процесс из директории модуля `support-assistant`, поэтому сервис автоматически пробует оба варианта путей:

- `support-assistant/data` и `support-assistant/prompts/...` при запуске из корня репозитория;
- `data` и `prompts/...` при запуске из директории модуля.

Если нужно явно зафиксировать пути для запуска через Gradle, используйте:

```bash
SUPPORT_PORT=8091 \
SUPPORT_DATA_DIR=data \
SUPPORT_PROMPT_PATH=prompts/support_assistant_prompt.md \
./gradlew :support-assistant:run
```

Если `SUPPORT_DATA_DIR` или `SUPPORT_PROMPT_PATH` заданы вручную, они должны быть абсолютными или относительными к текущей working directory процесса.

### Если порт 8091 занят

Если запуск падает с ошибкой:

```text
java.net.BindException: Address already in use
```

значит порт `8091` уже занят другим процессом. Это не ошибка RAG, MCP или LLM; сервис просто не смог открыть HTTP port.

Проверить, кто слушает порт:

```bash
lsof -nP -iTCP:8091 -sTCP:LISTEN
```

Для демо безопаснее не останавливать чужой процесс, а запустить support assistant на другом порту:

```bash
SUPPORT_PORT=8092 ./gradlew :support-assistant:run
```

Тогда health и все curl-примеры нужно выполнять через выбранный порт:

```bash
curl -s http://localhost:8092/health
```

```bash
curl -s http://localhost:8092/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему не работает авторизация?","userId":"user_001","ticketId":"ticket_123"}'
```

Если процесс на `8091` точно не нужен, его можно остановить и оставить default port:

```bash
kill <PID>
./gradlew :support-assistant:run
```

## Быстрая демонстрация работоспособности

Эта проверка показывает весь pipeline `/support/ask`: HTTP API, mock CRM MCP, support RAG, prompt assembly и graceful degradation, если LLM backend не запущен.

1. Запустить сервис:

```bash
./gradlew :support-assistant:run
```

2. В другом терминале проверить health:

```bash
curl -s http://localhost:8091/health
```

Ожидаемо:

```json
{
  "status": "ok",
  "service": "support-assistant",
  "llmBackend": "private",
  "ragReady": true
}
```

3. Проверить mock CRM MCP endpoints:

```bash
curl -s http://localhost:8091/support/users/user_001
curl -s http://localhost:8091/support/tickets/ticket_123
```

В ответах должны быть данные из `data/users.json` и `data/tickets.json`. Это демонстрирует, что support assistant получает user/ticket context отдельно от Android chat flow.

4. Вызвать основной сценарий авторизации:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{
    "question": "Почему не работает авторизация?",
    "userId": "user_001",
    "ticketId": "ticket_123"
  }'
```

Если `private-ai-service` не запущен, ответ все равно будет `200 OK`, но с `confidence: "low"` и текстом про недоступность LLM backend. Это нормальная degraded-проверка: в ответе должны быть:

- `ticketContextUsed: true`
- `userContextUsed: true`
- `sources` с `scope: "SUPPORT_FAQ"` и секцией `auth`
- `suggestedActions` с шагами по авторизации
- `answer`, собранный из FAQ и mock CRM context

5. Проверить сценарий без тикета:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему пропала история сообщений?","userId":"user_002"}'
```

Ожидаемо:

- `userContextUsed: true`
- `ticketContextUsed: false`
- источники по `chat_history`

6. Проверить неизвестного пользователя:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему не работает авторизация?","userId":"unknown_user","ticketId":"ticket_123"}'
```

Ожидаемо:

- сервис не падает
- `userContextUsed: false`
- `ticketContextUsed: true`
- в `suggestedActions` есть рекомендация проверить корректность `userId`

## Полная демонстрация с LLM

Для полноценного ответа от модели нужно поднять LLM backend. Самый близкий к текущей архитектуре вариант — использовать существующий `private-ai-service`.

1. Запустить Ollama и убедиться, что нужная модель доступна:

```bash
ollama list
ollama pull qwen2.5:3b-instruct
```

2. Запустить private AI service на порту `8085`:

```bash
PRIVATE_AI_API_KEY=dev-secret \
OLLAMA_BASE_URL=http://localhost:11434 \
DEFAULT_MODEL=qwen2.5:3b-instruct \
./gradlew :private-ai-service:run
```

3. В отдельном терминале запустить support assistant и указать ключ для вызова private AI service:

```bash
SUPPORT_LLM_BACKEND=private \
SUPPORT_LLM_BASE_URL=http://localhost:8085 \
SUPPORT_LLM_API_KEY=dev-secret \
SUPPORT_LLM_MODEL=qwen2.5:3b-instruct \
./gradlew :support-assistant:run
```

4. Выполнить основной запрос:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему не работает авторизация?","userId":"user_001","ticketId":"ticket_123"}'
```

При успешном LLM-вызове `answer` будет ответом модели в support-формате:

- краткий ответ
- вероятная причина
- что сделать пользователю
- что проверить поддержке
- источники
- уверенность

Дополнительно в логах support assistant должны быть строки вида:

```text
support_request_received userId=user_001 ticketId=ticket_123
support_mcp_result tool=get_user_by_id success=true
support_mcp_result tool=get_ticket_by_id success=true
support_retrieval count=...
support_llm_success backend=private latencyMs=...
```

## Автоматические тесты

Быстрая проверка нового модуля:

```bash
./gradlew :support-assistant:test
```

Проверка, что Android Kotlin flow не сломан:

```bash
./gradlew :app:compileDebugKotlin
```

## Env

```bash
SUPPORT_HOST=0.0.0.0
SUPPORT_PORT=8091
SUPPORT_DATA_DIR=support-assistant/data
SUPPORT_PROMPT_PATH=support-assistant/prompts/support_assistant_prompt.md
SUPPORT_RAG_TOP_K=4
SUPPORT_RAG_MAX_CONTEXT_CHARS=4000

SUPPORT_LLM_BACKEND=private
SUPPORT_LLM_BASE_URL=http://localhost:8085
SUPPORT_LLM_API_KEY=
SUPPORT_LLM_MODEL=qwen2.5:3b-instruct
SUPPORT_LLM_TIMEOUT_MS=120000
```

Backends:

- `private`: вызывает `SUPPORT_LLM_BASE_URL/v1/chat`, совместимо с текущим `private-ai-service`.
- `ollama`: вызывает `SUPPORT_LLM_BASE_URL/api/chat`.
- `cloud`: вызывает OpenAI-compatible chat completions endpoint из `SUPPORT_LLM_BASE_URL`.

Секреты не хардкодятся и не логируются.

## Пример

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{
    "question": "Почему не работает авторизация?",
    "userId": "user_001",
    "ticketId": "ticket_123"
  }'
```

Ответ:

```json
{
  "answer": "...",
  "sources": [
    {
      "title": "Авторизация через Google",
      "path": "support-assistant/data/faq.json",
      "section": "auth",
      "scope": "SUPPORT_FAQ",
      "score": 4.0
    }
  ],
  "ticketContextUsed": true,
  "userContextUsed": true,
  "suggestedActions": ["..."],
  "confidence": "medium"
}
```

## Тестовые сценарии

Авторизация:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему не работает авторизация?","userId":"user_001","ticketId":"ticket_123"}'
```

История чата:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему пропала история сообщений?","userId":"user_002","ticketId":"ticket_124"}'
```

Local LLM:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему локальная модель не отвечает?","userId":"user_001","ticketId":"ticket_126"}'
```

RAG:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему ассистент не находит документы?","userId":"user_001"}'
```

Нет тикета:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему пропала история сообщений?","userId":"user_002"}'
```

Неизвестный пользователь:

```bash
curl -s http://localhost:8091/support/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Почему не работает авторизация?","userId":"unknown_user","ticketId":"ticket_123"}'
```

## Как работает RAG

`SupportRagRetriever` загружает только `support-assistant/data/faq.json`, нормализует русский/английский текст, считает keyword/tag overlap и возвращает top-k фрагментов. Источники имеют scope `SUPPORT_FAQ` или `SUPPORT_DOCS`; fitness knowledge base не читается.

## Как работает mock CRM MCP

`JsonMockCrmMcp` предоставляет allowlisted tools:

- `get_user_by_id`
- `get_ticket_by_id`
- `search_tickets_by_user`

Tools читают только `users.json` и `tickets.json` из настроенной data directory. Не выполняются shell-команды и не читаются произвольные пути.

## Замена JSON на реальную CRM

Для интеграции с CRM нужно заменить реализацию MCP client/tools за тем же контрактом:

- `get_user_by_id(userId)`
- `get_ticket_by_id(ticketId)`
- `search_tickets_by_user(userId)`

Остальная цепочка `/support/ask -> MCP context -> support RAG -> prompt -> LLM` не меняется.

## Проверка

```bash
./gradlew :support-assistant:test
```
