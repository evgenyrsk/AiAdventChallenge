# Fitness Video Demo

Короткий сценарий записи видео, чтобы показать, что функционал работает.

Цель видео:

- показать локальное создание индекса
- показать embeddings и metadata
- показать сравнение `fixed_size` и `structure_aware`
- показать, что Android app использует этот индекс

## 1. Поднять MCP серверы

Открой терминал и выполни:

```bash
./gradlew :mcp-server:runMultiServer
```

В кадре должно быть видно:

- запуск всех MCP серверов
- `Document Index` на `localhost:8084`

## 2. Проиндексировать fitness corpus

Во втором терминале выполни:

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
```

В кадре должно быть видно:

- вызов `index_documents`
- `get_index_stats`
- `compare_chunking_strategies`
- `list_indexed_documents`

На что обратить внимание:

- `successfulDocuments`
- `corpusStats`
- две стратегии: `fixed_size` и `structure_aware`

## 3. Показать локальные артефакты

Выполни:

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

В кадре должно быть видно:

- `document_index.db`
- `local_docs_fixed_size_index.json`
- `local_docs_structure_aware_index.json`
- `local_docs_indexing_report.json`

## 4. Показать metadata и embeddings

Открой JSON export:

```bash
sed -n '1,120p' mcp-server/output/document-index/export/local_docs_structure_aware_index.json
```

В кадре нужно показать поля:

- `chunk_id`
- `source`
- `title`
- `section`
- `chunking_strategy`
- `document_type`
- `position_start`
- `position_end`
- `embedding`

Этого достаточно, чтобы визуально доказать:

- chunk сохраняется
- metadata сохраняются
- embeddings сохраняются

## 5. Показать сравнение стратегий

Выполни:

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

В кадре должно быть видно:

- `chunkCount`
- `averageChunkLength`
- `metadataCoverage`
- `recommendation`

Это подтверждает, что:

- обе стратегии реально работают
- результат у них различается
- сравнение формируется автоматически

## 6. Показать Android retrieval

Запусти Android app на эмуляторе и открой чат.

Задай 2 вопроса:

- `Сколько белка нужно при наборе массы?`
- `Что лучше для новичка: full body или upper lower?`

В кадре должно быть видно:

- ответ модели
- `Knowledge Base Context`
- найденные source chunks

Это показывает, что:

- индекс не просто создан
- он уже используется в приложении

## 7. Оптимальная длина видео

Рекомендуемый формат:

- 3–5 минут
- один непрерывный прогон
- без долгих объяснений

## 8. Минимальный результат, который должен попасть в видео

Если совсем коротко, на видео обязательно должны быть:

1. запуск индексации
2. SQLite index / JSON exports
3. chunk с metadata и embedding
4. comparison двух стратегий
5. один retrieval-кейс в Android app
