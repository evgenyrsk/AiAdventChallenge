# Private AI Service

Локальный `private-ai-service` поднимает приватный HTTP gateway поверх Ollama и даёт Android-приложению отдельный backend `PRIVATE_AI_SERVICE`.

## Что есть

- `GET /health`
- `POST /v1/chat`
- Bearer API key auth
- простой in-memory rate limit
- лимиты на `messages`, `input chars`, `maxTokens`, `contextWindow`
- normalized response contract, не завязанный на raw Ollama DTO

## Локальный запуск на MacBook / home server

### 1. Поднять Ollama

```bash
ollama serve
ollama list
```

Проверьте, что нужная модель доступна, например `qwen2.5:3b-instruct`.

### 2. Задать переменные окружения

```bash
export PRIVATE_AI_API_KEY=macbook-ollama-private-service-x7k29p
export OLLAMA_BASE_URL=http://localhost:11434
export DEFAULT_MODEL=qwen2.5:3b-instruct
export PORT=8085
```

Опционально можно переопределить:

```bash
export RATE_LIMIT_REQUESTS=10
export RATE_LIMIT_WINDOW_SECONDS=60
export MAX_MESSAGES=24
export MAX_INPUT_CHARS=12000
export MAX_OUTPUT_TOKENS=700
export MAX_CONTEXT_WINDOW=4096
export REQUEST_TIMEOUT_MS=120000
```

### 3. Запустить gateway

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :private-ai-service:run
```

Сервис поднимется на `http://0.0.0.0:8085`.

## Проверка

### Health

```bash
curl http://localhost:8085/health
```

Ожидаемый ответ:

```json
{
  "status": "ok",
  "ollamaAvailable": true,
  "model": "qwen2.5:3b-instruct",
  "ollamaBaseUrl": "http://localhost:11434"
}
```

### Chat

```bash
curl -X POST http://localhost:8085/v1/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer macbook-ollama-private-service-x7k29p" \
  -d '{
    "messages": [
      { "role": "system", "content": "You are concise." },
      { "role": "user", "content": "Say hello in one sentence." }
    ],
    "model": "qwen2.5:3b-instruct",
    "temperature": 0.2,
    "maxTokens": 128,
    "contextWindow": 2048
  }'
```

### Smoke check

```bash
PRIVATE_AI_API_KEY=macbook-ollama-private-service-x7k29p scripts/private-ai-service-smoke.sh
```

Скрипт делает несколько последовательных запросов и печатает `success/failure`, `latency` и `outputChars`.

## Android integration

В настройках чата выберите backend `Private AI Service` и заполните:

- `Base URL`
- `API key`
- `Model`
- `Timeout`

Для debug defaults можно добавить в `local.properties`:

```properties
PRIVATE_AI_SERVICE_BASE_URL=http://10.0.2.2:8085
PRIVATE_AI_SERVICE_API_KEY=change-me
PRIVATE_AI_SERVICE_MODEL=qwen2.5:3b-instruct
```

## Адреса подключения

### Android Emulator

Используйте:

```text
http://10.0.2.2:8085
```

### Физическое устройство

Используйте LAN IP вашего MacBook:

```text
http://<macbook-local-ip>:8085
```

### VPS

После переноса меняется только `baseUrl`, например:

```text
https://<domain-or-vps-ip>
```

Android chat flow менять не нужно.

## Перенос на VPS

На VPS остаются те же части:

- Ollama как runtime
- `private-ai-service` как HTTP gateway
- тот же Bearer auth механизм
- тот же Android backend `PRIVATE_AI_SERVICE`

Меняются только:

- host/base URL
- способ запуска процесса
- внешний TLS / reverse proxy при необходимости
