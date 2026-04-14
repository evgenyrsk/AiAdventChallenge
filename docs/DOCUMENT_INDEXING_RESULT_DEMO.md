# Document Indexing Result Demo

Как продемонстрировать итоговый результат:

> Локальный индекс документов с эмбеддингами + метаданные + сравнение 2 стратегий chunking

## Цель демонстрации

В ходе demo нужно доказать 4 вещи:

1. индекс локально создается
2. у чанков есть embeddings и metadata
3. существуют две стратегии chunking
4. результаты этих стратегий различаются и сравниваются

## Сценарий демонстрации

### 1. Поднять MCP серверы

```bash
./gradlew :mcp-server:runMultiServer
```

Что проговорить:

- document index server доступен на `localhost:8084`
- Android эмулятор использует тот же server через `10.0.2.2:8084`

### 2. Построить локальный индекс

Используй стабильный demo corpus:

```bash
bash scripts/document-indexing-smoke.sh demo/document-indexing-corpus local_docs
```

Для fitness-specific demo используй:

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
```

Что проговорить:

- индексируется локальный mixed corpus
- запускаются обе стратегии:
  - `fixed_size`
  - `structure_aware`
- pipeline проходит этапы:
  - load documents
  - chunking
  - embedding generation
  - index persistence

### 3. Показать, что индекс реально создан

Покажи артефакты:

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

Ожидаемые доказательства:

- `mcp-server/output/document-index/document_index.db`
- `local_docs_fixed_size_index.json`
- `local_docs_structure_aware_index.json`
- `local_docs_indexing_report.json`

Что проговорить:

- SQLite является primary storage
- JSON используется как export/debug format

### 4. Показать embeddings и metadata

Перед JSON-экспортом полезно показать, что данные реально лежат в SQLite:

```bash
sqlite3 mcp-server/output/document-index/document_index.db ".tables"
sqlite3 mcp-server/output/document-index/document_index.db ".schema indexed_chunks"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT chunk_id, title, section, chunking_strategy, document_type FROM indexed_chunks ORDER BY title LIMIT 10;"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT chunk_id, substr(metadata_json, 1, 200), substr(embedding_json, 1, 200) FROM indexed_chunks LIMIT 3;"
sqlite3 mcp-server/output/document-index/document_index.db \
  "SELECT source, strategy, document_count, chunk_count, average_chunk_length FROM strategy_summaries ORDER BY strategy;"
```

Что проговорить:

- `document_index.db` является primary storage
- `indexed_chunks` хранит текст, metadata и embedding vector
- `strategy_summaries` хранит агрегаты по стратегиям
- JSON export ниже нужен для inspect/debug без SQL

Открой экспорт одной стратегии:

```bash
sed -n '1,120p' mcp-server/output/document-index/export/local_docs_structure_aware_index.json
```

Что нужно показать в chunk:

- `chunk_id`
- `source`
- `title`
- `section`
- `chunking_strategy`
- `document_type`
- `position_start`
- `position_end`
- `embedding`

Что проговорить:

- каждый chunk сохраняется не только как текст
- вместе с ним сохраняются embeddings и metadata
- это является основой для будущего retrieval и citations

### 5. Показать сравнение двух стратегий

Сравнение можно показать через smoke output или отдельным запросом:

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":10,
    "method":"compare_chunking_strategies",
    "params":{
      "source":"local_docs",
      "path":"'"$(pwd)"'/demo/document-indexing-corpus"
    }
  }'
```

На что обратить внимание:

- `chunkCount`
- `averageChunkLength`
- `metadataCoverage`
- итоговая `recommendation`

Что проговорить:

- `fixed_size` дает более предсказуемые размеры chunk’ов
- `structure_aware` лучше сохраняет смысловые границы документа
- обе стратегии работают на одном и том же корпусе, поэтому сравнение корректно

## Усиленный demo через Android app

После индексации можно показать, что результат уже пригоден для retrieval в приложении.

### 6. Запустить Android app на эмуляторе

Открой экран чата и задай knowledge-запросы:

- `Чем fixed_size chunking отличается от structure_aware?`
- `Зачем у chunk'ов нужны metadata?`
- `Какой storage используется для document index?`

Если индексирован fitness corpus, используй:

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`

Что показать:

- ответ модели
- `Knowledge Base Context` card
- source / strategy / найденные chunks

Что проговорить:

- это доказывает, что индекс уже используется приложением
- retrieval идет через MCP server поверх локального индекса

### 7. Android app database нужно показывать отдельно

Если в demo хотят посмотреть локальную БД приложения, это другая база:

```bash
adb shell run-as com.example.aiadventchallenge ls databases
adb shell run-as com.example.aiadventchallenge sqlite3 databases/app_database ".tables"
```

Что проговорить:

- `app_database` хранит чат и локальные сущности Room
- document retrieval index хранится не в приложении, а на MCP server side в `document_index.db`

## Важно про legacy docs

Упоминания `./fitness_data.db` в старых markdown относятся к legacy fitness summary flow и не являются актуальным storage для document indexing demo.

## Короткая формула результата

Если нужно завершить демонстрацию одной фразой:

> Система локально индексирует документы, строит embeddings, сохраняет metadata, поддерживает две стратегии chunking и позволяет их сравнивать на одном и том же корпусе.
