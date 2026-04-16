# Fitness Demo Script

Короткий сценарий демонстрации, что задача выполнена:

> локальный индекс документов с эмбеддингами + retrieval -> self-hosted rerank -> final context в Android приложении

## 1. Поднять MCP серверы

```bash
./gradlew :mcp-server:runMultiServer
```

Проверка:

- document index server доступен на `localhost:8084`
- Android эмулятор сможет ходить к нему через `10.0.2.2:8084`

## 2. Поднять self-hosted reranker

```bash
cd tools/reranker-service
./run.sh
```

Проверка:

```bash
curl -s http://localhost:8091/health
```

Что сказать:

- rerank-модель работает как отдельный локальный sidecar
- Android приложение напрямую в неё не ходит
- вызов идёт через `mcp-server` внутри retrieval pipeline

## 3. Проиндексировать fitness corpus

```bash
bash scripts/reindex-fitness-knowledge.sh
```

Что это доказывает:

- документы читаются локально
- запускаются обе стратегии:
  - `fixed_size`
  - `structure_aware`
- pipeline проходит:
  - loading
  - chunking
  - embedding generation
  - index persistence

## 4. Показать, что индекс реально создан

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

Что показать:

- `mcp-server/output/document-index/document_index.db`
- `fitness_knowledge_fixed_size_index.json`
- `fitness_knowledge_structure_aware_index.json`
- `fitness_knowledge_indexing_report.json`

Что сказать:

- индекс хранится локально в SQLite
- JSON используется для inspect/debug

## 5. Показать содержимое SQLite index

Основная БД этого demo:

```bash
sqlite3 mcp-server/output/document-index/document_index.db ".tables"
sqlite3 mcp-server/output/document-index/document_index.db ".schema indexed_chunks"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT chunking_strategy, COUNT(*) AS chunk_count FROM indexed_chunks GROUP BY chunking_strategy ORDER BY chunking_strategy;"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT chunk_id, title, section, chunking_strategy, document_type FROM indexed_chunks ORDER BY title LIMIT 10;"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT chunk_id, substr(metadata_json, 1, 200), substr(embedding_json, 1, 200) FROM indexed_chunks LIMIT 3;"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT source, strategy, document_count, chunk_count, average_chunk_length FROM strategy_summaries ORDER BY strategy;"
```

Что показать:

- таблицы `indexed_chunks` и `strategy_summaries`
- реальный `chunk_id`
- `title`, `section`, `chunking_strategy`, `document_type`
- `metadata_json` и начало `embedding_json`
- агрегаты по стратегиям

Что сказать:

- SQLite является primary storage для локального document index
- JSON рядом нужен как удобный export/debug view
- inspect можно делать либо через SQL, либо через export-файлы

## 6. Показать metadata и embeddings через JSON export

```bash
sed -n '1,120p' mcp-server/output/document-index/export/fitness_knowledge_structure_aware_index.json
```

Что показать в chunk:

- `chunk_id`
- `source`
- `title`
- `section`
- `chunking_strategy`
- `document_type`
- `position_start`
- `position_end`
- `embedding`

Что сказать:

- chunk сохраняется не только как текст
- вместе с ним сохраняются metadata и embedding vector

## 7. Показать сравнение двух стратегий

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":10,
    "method":"compare_chunking_strategies",
    "params":{
      "source":"fitness_knowledge",
      "path":"'"$(pwd)"'/demo/fitness-knowledge-corpus"
    }
  }'
```

Что показать:

- `chunkCount`
- `averageChunkLength`
- `metadataCoverage`
- `recommendation`

Что сказать:

- `fixed_size` дает более равномерные chunks
- `structure_aware` лучше сохраняет смысловые границы

## 8. Показать локальную БД Android app отдельно

Локальная Room БД приложения называется `app_database`. Это не document index, а хранилище чата и связанных сущностей.

Варианты просмотра:

```bash
adb shell run-as com.example.aiadventchallenge ls databases
adb shell run-as com.example.aiadventchallenge sqlite3 databases/app_database ".tables"
adb shell run-as com.example.aiadventchallenge sqlite3 databases/app_database \
  "SELECT id, isFromUser, substr(content, 1, 80), timestamp FROM chat_messages ORDER BY timestamp DESC LIMIT 10;"
```

Что показать:

- таблицы `chat_messages`, `summaries`, `branches`, `chat_settings`, `ai_requests`
- последние сообщения чата

Что сказать:

- `app_database` хранит локальное состояние Android app
- retrieval index живет отдельно в `mcp-server/output/document-index/document_index.db`
- если нужен GUI, можно использовать Android Studio App Inspection для Room

## 9. Показать retrieval, rerank и сравнение режимов через Android app

Запусти app на эмуляторе и открой чат.

Покажи:

- переключатель режима `Обычный / RAG Basic / RAG Enhanced`
- один и тот же вопрос сначала в `Обычный`, потом в `RAG Basic`, затем в `RAG Enhanced`
- `Knowledge Base Context` card для RAG-режимов
- в `RAG Enhanced` показать:
  - `originalQuery`
  - `rewrittenQuery`
  - `topK before -> after`
  - `postProcessingMode`
  - `rerankProvider`
  - `rerankModel`
  - `rerank candidates`
  - `rerank fallback`, если sidecar недоступен
  - отброшенные чанки и причины

Рекомендуемые вопросы:

- `Что важнее для похудения: дефицит калорий или время приёма пищи?`
- `Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?`
- `Почему сон влияет на восстановление и контроль аппетита?`
- `Почему жидкие калории могут мешать снижению веса?`

Что показать:

- ответ модели без RAG
- ответ модели с RAG
- `Knowledge Base Context` card
- source / strategy / найденные chunks
- title / section / score / rerank score / final rank

Что сказать:

- `PLAIN_LLM` отправляет вопрос в LLM без retrieval
- `RAG Basic` делает базовый retrieval без rewrite/post-processing
- `RAG Enhanced` делает rewrite, затем retrieval, затем self-hosted model rerank, затем threshold/fallback и только после этого собирает prompt
- retrieval идет через MCP server поверх локального индекса source `fitness_knowledge`
- model rerank живёт отдельно от Android UI и вызывается только на серверной стороне

## 10. Показать evaluation runner

```bash
AI_API_KEY=... ./gradlew :mcp-server:runFitnessRagEvaluation
```

Что показать:

- runner читает `demo/fitness-knowledge-corpus/fixtures/rag_questions.json`
- для каждого вопроса делает:
  - `PLAIN_LLM`
  - retrieval only
  - heuristic rerank
  - model rerank
  - threshold + model rerank
- сохраняет результаты в:
  - `output/fitness-rag-evaluation/results.json`
  - `output/fitness-rag-evaluation/report.md`

Что сказать:

- это отдельный reproducible способ сравнения качества
- runner полезен и для demo, и для полуавтоматической проверки

## 11. Важно про legacy docs

Если в других markdown встречается `./fitness_data.db`, это legacy-описание старого fitness summary flow. Для текущего demo из этого файла актуальная БД: `mcp-server/output/document-index/document_index.db`.

## 12. Финальная фраза

> Система локально индексирует fitness-документы, строит embeddings, выполняет retrieval, затем self-hosted rerank как второй этап и отдаёт в Android приложение уже очищенный финальный контекст для ответа LLM.
