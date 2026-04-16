# Fitness Video Demo

Короткий сценарий записи видео, чтобы показать, что функционал работает.

Цель видео:

- показать локальное создание индекса
- показать embeddings и metadata
- показать сравнение `fixed_size` и `structure_aware`
- показать, что Android app использует этот индекс
- показать self-hosted reranker
- показать разницу между `RAG Basic`, heuristic rerank и `RAG Enhanced`

Что важно зафиксировать в кадре:

- `RAG Basic` использует базовый retrieval без rewrite и reranking
- `RAG Enhanced` показывает `rewrittenQuery`, model rerank, fallback/debug и более чистый финальный контекст
- в debug card видны кандидаты до/после фильтрации и финальные источники

## 1. Поднять MCP серверы

Открой терминал и выполни:

```bash
./gradlew :mcp-server:runMultiServer
```

В кадре должно быть видно:

- запуск всех MCP серверов
- `Document Index` на `localhost:8084`

## 2. Поднять self-hosted reranker

Во втором терминале выполни:

```bash
cd tools/reranker-service
./run.sh
```

В кадре должно быть видно:

- старт локального HTTP service
- health-check на `localhost:8091`
- модель `BAAI/bge-reranker-base`

## 3. Проиндексировать fitness corpus

В третьем терминале выполни:

```bash
bash scripts/reindex-fitness-knowledge.sh
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

## 4. Показать локальные артефакты

Выполни:

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

В кадре должно быть видно:

- `document_index.db`
- `fitness_knowledge_fixed_size_index.json`
- `fitness_knowledge_structure_aware_index.json`
- `fitness_knowledge_indexing_report.json`

## 5. Показать metadata и embeddings

Открой JSON export:

```bash
sed -n '1,120p' mcp-server/output/document-index/export/fitness_knowledge_structure_aware_index.json
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

## 6. Показать сравнение стратегий

Выполни:

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

В кадре должно быть видно:

- `chunkCount`
- `averageChunkLength`
- `metadataCoverage`
- `recommendation`

Это подтверждает, что:

- обе стратегии реально работают
- результат у них различается
- сравнение формируется автоматически

## 7. Показать Android сравнение `Обычный` vs `RAG`

Запусти Android app на эмуляторе и открой чат.

Задай один и тот же вопрос в двух режимах:

- `Что важнее для похудения: дефицит калорий или время приёма пищи?`
- `Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?`

В кадре должно быть видно:

- переключатель режима ответа
- ответ без RAG
- ответ в `RAG Basic`
- ответ в `RAG Enhanced`
- `Knowledge Base Context`
- найденные source chunks
- score / section / rerank score / final rank
- `rerankProvider` и `rerankModel` в details sheet

Это показывает, что:

- индекс не просто создан
- приложение умеет работать в двух режимах
- `RAG Enhanced` реально использует retrieval -> rerank pipeline по corpus `fitness_knowledge`

## 8. Показать evaluation runner

В четвёртом терминале выполни:

```bash
AI_API_KEY=... ./gradlew :mcp-server:runFitnessRagEvaluation
```

В кадре должно быть видно:

- последовательный прогон 10 вопросов
- сравнение retrieval-only / heuristic / model / enhanced
- генерация `results.json`
- генерация `report.md`

## 9. Оптимальная длина видео

Рекомендуемый формат:

- 3–5 минут
- один непрерывный прогон
- без долгих объяснений

## 10. Минимальный результат, который должен попасть в видео

Если совсем коротко, на видео обязательно должны быть:

1. запуск индексации
2. запуск self-hosted reranker
3. SQLite index / JSON exports
4. chunk с metadata и embedding
5. comparison двух стратегий
6. один сравнительный кейс `Обычный` vs `RAG Basic` vs `RAG Enhanced` в Android app
7. один запуск evaluation runner
