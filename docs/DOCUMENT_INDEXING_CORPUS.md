# Document Indexing Corpus

Stable demo corpus for the document-indexing assignment:

- repository root `README.md`
- all files under `docs/`
- Kotlin sources under `mcp-server/src/main/kotlin/`
- `demo/document-indexing-corpus/` for technical retrieval demos
- `demo/fitness-knowledge-corpus/` for domain-specific fitness retrieval demos

Optional corpus extension:

- one PDF dropped into `docs/` before running indexing

Why this corpus:

- mixes markdown, architecture notes and source code
- is stable and reproducible inside the repository
- already exceeds the assignment minimum when indexed from repo root

Recommended MCP call:

```json
{
  "path": "/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge",
  "source": "local_docs",
  "strategies": ["fixed_size", "structure_aware"]
}
```

Evidence to capture after indexing:

- `successfulDocuments`
- `corpusStats.documentCount`
- `corpusStats.totalCharacters`
- `corpusStats.totalWords`
- `strategySummaries`
- `mcp-server/output/document-index/document_index.db`
- JSON exports under `mcp-server/output/document-index/export/`

Recommended corpus by scenario:

- technical/architecture demo:
  - `demo/document-indexing-corpus/`
- product/fitness demo:
  - `demo/fitness-knowledge-corpus/`
