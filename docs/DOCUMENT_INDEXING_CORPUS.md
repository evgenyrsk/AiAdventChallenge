# Корпус для document indexing

Стабильный demo corpus для задачи по document indexing:

- корневой `README.md` репозитория
- все файлы в `docs/`
- исходники Kotlin в `mcp-server/src/main/kotlin/`
- `demo/document-indexing-corpus/` для технических retrieval-демо
- `demo/fitness-knowledge-corpus/content/` для retrieval-демо в фитнес-домене

Дополнительное расширение корпуса:

- один PDF-файл, добавленный в `docs/` перед запуском индексации

Почему выбран именно этот корпус:

- сочетает markdown, архитектурные заметки и исходный код
- остаётся стабильным и воспроизводимым внутри репозитория
- уже превышает минимальные требования задания при индексации из корня репозитория

Рекомендуемый MCP-вызов:

```json
{
  "path": "/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge",
  "source": "local_docs",
  "strategies": ["fixed_size", "structure_aware"]
}
```

Что стоит зафиксировать после индексации:

- `successfulDocuments`
- `corpusStats.documentCount`
- `corpusStats.totalCharacters`
- `corpusStats.totalWords`
- `strategySummaries`
- `mcp-server/output/document-index/document_index.db`
- JSON-экспорты в `mcp-server/output/document-index/export/`

Рекомендуемый корпус по сценариям:

- техническое/архитектурное демо:
  - `demo/document-indexing-corpus/`
- продуктовое/фитнес-демо:
  - `demo/fitness-knowledge-corpus/content/`
