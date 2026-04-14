# Демо document indexing

Рекомендуемый demo corpus:

- `demo/document-indexing-corpus/` как стабильный smoke corpus внутри репозитория
- корневой `README.md` + `docs/` + `mcp-server/src/main/kotlin/` как более крупный корпус масштаба проекта
- один локальный PDF, добавленный в любой из этих корпусов, если нужно вручную проверить PDF indexing

Smoke corpus детерминирован и его легко проверять вручную. Более крупный корпус
проекта уже содержит достаточно markdown, архитектурных заметок и исходного кода,
чтобы превысить целевой MVP-объём. Пайплайн индексации пропускает `.git`,
`.gradle`, `.idea`, `build`, `.kotlin` и `output`.

Запустите выделенный MCP server:

```bash
./gradlew :mcp-server:runDocumentIndexServer
```

Примеры вызовов инструментов:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "index_documents",
  "params": {
    "path": "/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge",
    "source": "local_docs",
    "strategies": ["fixed_size", "structure_aware"]
  }
}
```

Или запустите готовый smoke-сценарий:

```bash
bash scripts/document-indexing-smoke.sh
```

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "get_index_stats",
  "params": {
    "source": "local_docs"
  }
}
```

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "compare_chunking_strategies",
  "params": {
    "source": "local_docs",
    "path": "/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge"
  }
}
```

Артефакты:

- SQLite-индекс: `mcp-server/output/document-index/document_index.db`
- JSON-экспорты: `mcp-server/output/document-index/export/`
- Отчёт о приёмке: `mcp-server/output/document-index/export/local_docs_indexing_report.json`

Что проверять в ответе `index_documents`:

- `corpusStats.documentCount`
- `corpusStats.totalCharacters`
- `corpusStats.totalWords`
- две `strategySummaries`
- различие в количестве чанков или среднем размере чанков между стратегиями
