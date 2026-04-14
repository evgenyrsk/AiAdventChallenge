# Функциональность document indexing

Эта функциональность создаёт локальную основу для индексации документов, retrieval
и будущих workflow в стиле RAG.

## Что реализовано

- загрузка смешанных документов: markdown, text, code и PDF
- две стратегии chunking:
  - fixed size
  - structure aware
- расширяемая абстракция embeddings provider
- локальное хранение чанков, embeddings и metadata в SQLite
- экспорт в JSON для проверки и отладки
- MCP tools для индексации, статистики, сравнения и retrieval

## Основные MCP tools

- `index_documents`
- `reindex_documents`
- `get_index_stats`
- `compare_chunking_strategies`
- `list_indexed_documents`
- `search_index`
- `retrieve_relevant_chunks`
- `answer_with_retrieval`

## Рекомендуемое локальное демо

1. Запустите сервер:

```bash
./gradlew :mcp-server:runDocumentIndexServer
```

2. Выполните smoke-сценарий:

```bash
bash scripts/document-indexing-smoke.sh
```

3. Проверьте артефакты:

- `mcp-server/output/document-index/document_index.db`
- `mcp-server/output/document-index/export/`

## Подтверждение готовности

Функциональность считается завершённой для задания, если один локальный запуск показывает:

- индексацию смешанного корпуса
- сгенерированные embeddings
- сохранённые metadata для каждого чанка
- раздельные результаты для `fixed_size` и `structure_aware`
- результат сравнения стратегий
- локальный индекс, сохранённый в SQLite

См. также:

- `docs/DOCUMENT_INDEXING_CORPUS.md`
- `docs/FITNESS_KNOWLEDGE_CORPUS.md`
- `docs/FITNESS_DEMO_SCRIPT.md`
- `docs/FITNESS_VIDEO_DEMO.md`
- `docs/DOCUMENT_INDEXING_DEMO.md`
- `docs/DOCUMENT_INDEXING_DEMO_SCRIPT.md`
- `docs/DOCUMENT_INDEXING_RESULT_DEMO.md`
- `docs/DOCUMENT_INDEXING_ACCEPTANCE.md`
