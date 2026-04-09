# Reproduce: Ошибка LLM при отсутствии кредитов

Платформа: Android
Статус: Воспроизведён

## Входные данные
- Описание: Пользователь пытается использовать AI-ассистента, но его API ключ имеет недостаточно кредитов
- Stacktrace: `Provider error (status: 402): Insufficient credits.`
- API Key: `sk-JebNvHLJexTxau0m5XQTcSL4IzBDW-9E`

## Шаги воспроизведения
1. Пользователь вводит запрос: "Запиши мой вес 82.5 кг за сегодня, я сделал 10000 шагов"
2. Приложение вызывает `add_fitness_log` MCP tool успешно
3. Приложение отправляет запрос к LLM API (https://routerai.ru/api/v1/chat/completions)
4. API возвращает ошибку: `{"error":"Provider error (status: 402): Insufficient credits."}`
5. `HttpClient.kt:60` создаёт `HttpException(code=503, body=...)`
6. `ChatMessageHandlerImpl.kt:109-115` обрабатывает ошибку как `ChatResult.Error`
7. В лог записывается: `LLM Error: Error: HTTP 503`

## Фактический результат
- Пользователь видит общую ошибку "Error: HTTP 503"
- Неочевидно что проблема именно в отсутствии кредитов API
- Нет подсказки как исправить (пополнить баланс, сменить API ключ)

## Ожидаемый результат
- Понятное сообщение пользователю о том, что закончились кредиты
- Подсказка где пополнить баланс или как сменить API ключ
- MCP tool выполнился успешно (fitness log добавлен), но AI ответа нет

## Скриншоты / логи
```
2026-04-09 15:11:52.981 22449-22509 HttpClient D  Response: {"error":"Provider error (status: 402): Insufficient credits."}
2026-04-09 15:11:52.982 22449-22449 ChatMessageHandler E  LLM Error: Error: HTTP 503
```
