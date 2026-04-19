# Fitness Enhanced RAG Testing Guide

Инструкция для ручного тестирования и демонстрации задачи:

> query rewrite + filtering/reranking + сравнение `PLAIN_LLM` / `RAG Basic` / `RAG Enhanced` в Android приложении

## Что проверяем

Нужно подтвердить, что:

- fitness knowledge base успешно индексируется
- MCP server с document retrieval запущен и доступен Android приложению
- Android chat UI показывает 3 режима:
  - `Обычный`
  - `RAG Basic`
  - `RAG Enhanced`
- в `RAG Enhanced` видны:
  - `originalQuery`
  - `rewrittenQuery`
  - top-K до и после фильтрации
  - режим post-processing
  - финальные чанки
  - отброшенные чанки и причины отсечения
- один и тот же вопрос можно сравнить между режимами
- evaluation runner формирует отчёт по трём режимам

## Prerequisites

Перед прогоном должны быть доступны:

- Android Studio или настроенный Android SDK
- эмулятор Android или подключённое устройство
- `adb`
- локальный проект в актуальном состоянии
- indexable corpus `demo/fitness-knowledge-corpus/content`

## Быстрый сценарий

Если нужен короткий smoke-check, достаточно выполнить:

```bash
./gradlew :mcp-server:runMultiServer
```

Во втором терминале:

```bash
bash scripts/reindex-fitness-knowledge.sh
```

В третьем терминале:

```bash
./gradlew :app:installDebug
```

Дальше:

1. Открыть приложение на эмуляторе
2. Перейти в экран чата
3. Задать один и тот же вопрос в режимах `Обычный`, `RAG Basic`, `RAG Enhanced`
4. Убедиться, что в `RAG Enhanced` появился расширенный `Knowledge Base Context`

## Полный сценарий

## Шаг 1. Поднять MCP серверы

В корне проекта:

```bash
./gradlew :mcp-server:runMultiServer
```

Что это поднимает:

- nutrition server
- meal guidance server
- training guidance server
- document index server

Что проверить:

- `Document Index Server` доступен на `localhost:8084`
- Android эмулятор сможет обращаться к нему через `10.0.2.2:8084`

Опциональный smoke-check:

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"tools/list"
  }'
```

Ожидаемо в ответе должны присутствовать retrieval tools:

- `retrieve_relevant_chunks`
- `answer_with_retrieval`

## Шаг 2. Пересобрать индекс fitness knowledge base

Во втором терминале:

```bash
bash scripts/reindex-fitness-knowledge.sh
```

Что делает скрипт:

- берёт knowledge docs из `demo/fitness-knowledge-corpus/content`
- запускает document indexing pipeline
- строит индекс для source `fitness_knowledge`
- прогоняет smoke flow индексации

Что должно получиться:

- индексирование завершается без ошибки
- source называется `fitness_knowledge`
- создаются artefacts в `mcp-server/output/document-index`

## Шаг 3. Проверить, что индекс действительно создан

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

Что должно быть видно:

- `mcp-server/output/document-index/document_index.db`
- `fitness_knowledge_fixed_size_index.json`
- `fitness_knowledge_structure_aware_index.json`
- `fitness_knowledge_indexing_report.json`

Опционально можно показать SQLite содержимое:

```bash
sqlite3 mcp-server/output/document-index/document_index.db ".tables"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT chunking_strategy, COUNT(*) FROM indexed_chunks GROUP BY chunking_strategy ORDER BY chunking_strategy;"
```

## Шаг 4. Установить и запустить Android приложение

Если эмулятор уже запущен:

```bash
./gradlew :app:installDebug
```

Если нужно увидеть список устройств:

```bash
adb devices
```

При желании можно запускать и из Android Studio, но для demo удобно иметь CLI-команду выше.

После установки:

1. Открыть приложение
2. Перейти в экран чата

Что должно быть видно:

- поле ввода
- переключатель режима ответа
- режимы:
  - `Обычный`
  - `RAG Basic`
  - `RAG Enhanced`

## Шаг 5. Канонические вопросы для сравнения

Использовать один и тот же вопрос последовательно во всех трёх режимах.

Рекомендуемые вопросы:

- `Что важнее для похудения: дефицит калорий или время приёма пищи?`
- `Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?`
- `Почему сон влияет на восстановление и контроль аппетита?`
- `Почему жидкие калории могут мешать снижению веса?`

Лучший демонстрационный вопрос для enhanced pipeline:

```text
Почему сон влияет на восстановление и контроль аппетита?
```

Он хорошо показывает:

- rewrite в более retrieval-friendly запрос
- отбор релевантных fitness chunks
- уменьшение шума в финальном контексте

## Шаг 6. Проверить режим `Обычный`

1. Выбрать `Обычный`
2. Отправить канонический вопрос

Что ожидать:

- ответ приходит без retrieval
- блока `Knowledge Base Context` нет
- ответ может быть полезным, но не grounded на локальном corpus

## Шаг 7. Проверить режим `RAG Basic`

1. Выбрать `RAG Basic`
2. Повторно отправить тот же вопрос

Что ожидать:

- появляется блок `Knowledge Base Context`
- запрос идёт без rewrite
- retrieval выполняется без post-processing
- показываются найденные финальные чанки

Что важно проверить:

- `originalQuery` совпадает с вопросом пользователя
- `rewrittenQuery` отсутствует или пустой
- top-K до и после фильтрации фактически одинаковый
- источники выглядят релевантными, но могут содержать шум

## Шаг 8. Проверить режим `RAG Enhanced`

1. Выбрать `RAG Enhanced`
2. Повторно отправить тот же вопрос

Что ожидать:

- появляется расширенный блок `Knowledge Base Context`
- отображается `originalQuery`
- отображается `rewrittenQuery`
- видно `topK before -> after`
- видно `postProcessingMode`
- видно, какие чанки остались финальными
- видно, какие чанки были отброшены и почему

Что особенно важно проверить:

- rewrite действительно не “магический”, а читаемый и понятный
- retrieval query отличается от исходного вопроса только в нужную сторону
- после filtering/reranking финальный контекст чище, чем в `RAG Basic`
- ответ модели опирается на более релевантные источники

## Шаг 9. Что сравнивать между `RAG Basic` и `RAG Enhanced`

Для одного и того же вопроса зафиксировать:

- `originalQuery`
- `rewrittenQuery`
- количество кандидатов до фильтрации
- количество кандидатов после фильтрации
- какие источники попали в финальный prompt
- какие кандидаты были отброшены
- причина отсечения
- итоговый ответ модели

Ожидаемая qualitative разница:

- `RAG Basic` может тащить лишние чанки
- `RAG Enhanced` должен давать более чистый retrieval context
- в `RAG Enhanced` должно быть проще объяснить, почему попал каждый chunk

## Шаг 10. Evaluation runner

Для полуавтоматического сравнения режимов:

```bash
AI_API_KEY=... ./gradlew :mcp-server:runFitnessRagEvaluation
```

Runner:

- читает `demo/fitness-knowledge-corpus/fixtures/rag_questions.json`
- прогоняет:
  - `PLAIN_LLM`
  - `RAG Basic`
  - `RAG Enhanced`
- сохраняет результаты в:
  - `output/fitness-rag-evaluation/results.json`
  - `output/fitness-rag-evaluation/report.md`

Полезные env-параметры:

- `AI_BASE_URL`
- `AI_MODEL`
- `DOCUMENT_INDEX_SERVER_URL`
- `RAG_SOURCE`
- `RAG_STRATEGY`
- `RAG_TOP_K`
- `RAG_ENHANCED_TOP_K_BEFORE`
- `RAG_ENHANCED_TOP_K_AFTER`
- `RAG_ENHANCED_THRESHOLD`
- `RAG_MAX_CHARS`
- `RAG_PER_DOCUMENT_LIMIT`
- `RAG_EVAL_JSON`
- `RAG_EVAL_MARKDOWN`

Пример:

```bash
AI_API_KEY=... \
RAG_SOURCE=fitness_knowledge \
RAG_TOP_K=4 \
RAG_ENHANCED_TOP_K_BEFORE=6 \
RAG_ENHANCED_TOP_K_AFTER=4 \
RAG_ENHANCED_THRESHOLD=0.2 \
./gradlew :mcp-server:runFitnessRagEvaluation
```

## Шаг 11. Что показать на демо

Минимальный demo flow:

1. `./gradlew :mcp-server:runMultiServer`
2. `bash scripts/reindex-fitness-knowledge.sh`
3. `./gradlew :app:installDebug`
4. В Android app показать режимы `Обычный`, `RAG Basic`, `RAG Enhanced`
5. Задать один вопрос три раза
6. На `RAG Enhanced` раскрыть debug-детали
7. Показать evaluation runner report

Что проговорить голосом:

- `PLAIN_LLM` отвечает без retrieval
- `RAG Basic` использует локальный индекс, но ещё без rewrite и reranking
- `RAG Enhanced` сначала переписывает query, потом получает initial candidates, потом фильтрует/переранжирует их, и только после этого собирает final prompt
- pipeline прозрачен и пригоден для анализа

## Критерии успеха

Задача считается корректно проверенной, если:

- MCP серверы поднимаются без ошибки
- индекс `fitness_knowledge` пересобирается без ошибки
- Android app устанавливается и открывается
- доступны режимы `Обычный`, `RAG Basic`, `RAG Enhanced`
- `RAG Basic` показывает retrieval context
- `RAG Enhanced` показывает rewrite и post-processing debug info
- enhanced режим позволяет увидеть отфильтрованные кандидаты и причины отсечения
- ответы `RAG Enhanced` выглядят как минимум не хуже `RAG Basic`, а по демонстрационным вопросам обычно чище и точнее
- evaluation runner формирует JSON и Markdown отчёты по трём режимам

## См. также

- [docs/FITNESS_DEMO_SCRIPT.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_DEMO_SCRIPT.md)
- [docs/FITNESS_VIDEO_DEMO.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_VIDEO_DEMO.md)
- [docs/FITNESS_RAG_EVALUATION.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_RAG_EVALUATION.md)
- [docs/FITNESS_RAG_QUESTIONS.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_RAG_QUESTIONS.md)
