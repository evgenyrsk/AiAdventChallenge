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
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
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
- `local_docs_fixed_size_index.json`
- `local_docs_structure_aware_index.json`
- `local_docs_indexing_report.json`

Что сказать:

- индекс хранится локально в SQLite
- JSON используется для inspect/debug

## 4. Показать metadata и embeddings

```bash
sed -n '1,120p' mcp-server/output/document-index/export/local_docs_structure_aware_index.json
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

## 5. Показать сравнение двух стратегий

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":10,
    "method":"compare_chunking_strategies",
    "params":{
      "source":"local_docs",
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

## 6. Показать retrieval через Android app

Запусти app на эмуляторе и открой чат.

Задай 2–3 вопроса:

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`

Что показать:

- ответ модели
- `Knowledge Base Context` card
- source / strategy / найденные chunks

Что сказать:

- индекс уже используется приложением
- retrieval идет через MCP server поверх локального индекса

## 7. Финальная фраза

> Система локально индексирует fitness-документы, строит embeddings, сохраняет metadata, поддерживает две стратегии chunking и позволяет использовать этот индекс в retrieval-сценарии приложения.
