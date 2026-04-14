# Критерии приёмки для document indexing

Задача считается выполненной, если всё ниже можно показать за один локальный запуск.

## Обязательные подтверждения

1. `index_documents` успешно выполняется на demo corpus.
2. В ответе присутствует `corpusStats`, и его значения больше нуля:
   - количество документов
   - общее число символов
   - общее число слов
3. Выполняются две стратегии:
   - `fixed_size`
   - `structure_aware`
4. Сводки по стратегиям отличаются по распределению чанков.
5. Чанки содержат обязательные metadata:
   - `chunk_id`
   - `source`
   - `title`
   - `section`
   - `chunking_strategy`
6. Создаётся SQLite-индекс.
7. Создаются JSON-экспорты, включая:
   - экспорт чанков по каждой стратегии
   - экспорт отчёта об индексации

## Рекомендуемый порядок проверки

1. Запустить MCP server для индекса документов.
2. Вызвать `index_documents`.
3. Вызвать `get_index_stats`.
4. Вызвать `compare_chunking_strategies`.
5. При необходимости вызвать `list_indexed_documents`.

## Ожидаемые артефакты

- SQLite DB: `mcp-server/output/document-index/document_index.db`
- Экспорты чанков:
  - `local_docs_fixed_size_index.json`
  - `local_docs_structure_aware_index.json`
- Отчёт о приёмке:
  - `local_docs_indexing_report.json`

## Автоматическая проверка

Используйте:

```bash
./gradlew :mcp-server:test
```

Интеграционный тест проверяет индексацию смешанного корпуса, разбор PDF, наличие metadata, сравнение стратегий, генерацию отчёта и готовность к retrieval.
