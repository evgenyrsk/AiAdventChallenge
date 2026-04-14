# Fitness Demo Script

Короткий сценарий демонстрации, что задача выполнена:

> локальный индекс документов с эмбеддингами + метаданные + сравнение 2 стратегий chunking

## 1. Поднять MCP серверы

```bash
./gradlew :mcp-server:runMultiServer
```

Проверка:

- document index server доступен на `localhost:8084`
- Android эмулятор сможет ходить к нему через `10.0.2.2:8084`

## 2. Проиндексировать fitness corpus

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

## 3. Показать, что индекс реально создан

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

## 4. Показать содержимое SQLite index

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

## 5. Показать metadata и embeddings через JSON export

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

## 6. Показать сравнение двух стратегий

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

## 7. Показать локальную БД Android app отдельно

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

## 8. Показать retrieval и сравнение режимов через Android app

Запусти app на эмуляторе и открой чат.

Покажи:

- переключатель режима `Обычный / RAG`
- один и тот же вопрос сначала в `Обычный`, потом в `RAG`
- `Knowledge Base Context` card для режима `RAG`

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
- title / section / score

Что сказать:

- `PLAIN_LLM` отправляет вопрос в LLM без retrieval
- `RAG` делает поиск релевантных чанков, собирает augmented prompt и только потом вызывает LLM
- retrieval идет через MCP server поверх локального индекса source `fitness_knowledge`

## 9. Показать evaluation runner

```bash
AI_API_KEY=... ./gradlew :mcp-server:runFitnessRagEvaluation
```

Что показать:

- runner читает `demo/fitness-knowledge-corpus/fixtures/rag_questions.json`
- для каждого вопроса делает `PLAIN_LLM` и `RAG`
- сохраняет результаты в:
  - `output/fitness-rag-evaluation/results.json`
  - `output/fitness-rag-evaluation/report.md`

Что сказать:

- это отдельный reproducible способ сравнения качества
- runner полезен и для demo, и для полуавтоматической проверки

## 10. Важно про legacy docs

Если в других markdown встречается `./fitness_data.db`, это legacy-описание старого fitness summary flow. Для текущего demo из этого файла актуальная БД: `mcp-server/output/document-index/document_index.db`.

## 11. Финальная фраза

> Система локально индексирует fitness-документы, строит embeddings, сохраняет metadata, поддерживает две стратегии chunking и позволяет использовать этот индекс в retrieval-сценарии приложения.
