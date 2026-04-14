# Document Indexing Demo

Recommended demo corpus:

- `demo/document-indexing-corpus/` for a stable repo-local smoke corpus
- repository root `README.md` + `docs/` + `mcp-server/src/main/kotlin/` for a larger project-scale corpus
- one local PDF dropped into either corpus if you want to validate PDF indexing manually

The smoke corpus is deterministic and easy to inspect. The larger project corpus
already gives enough markdown, architecture notes and source code to exceed the
MVP volume target. The indexing pipeline skips `.git`, `.gradle`, `.idea`,
`build`, `.kotlin` and `output`.

Run the dedicated MCP server:

```bash
./gradlew :mcp-server:runDocumentIndexServer
```

Example tool calls:

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

Or run the ready-made smoke flow:

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

Artifacts:

- SQLite index: `mcp-server/output/document-index/document_index.db`
- JSON exports: `mcp-server/output/document-index/export/`
- Acceptance report: `mcp-server/output/document-index/export/local_docs_indexing_report.json`

What to verify in the `index_documents` response:

- `corpusStats.documentCount`
- `corpusStats.totalCharacters`
- `corpusStats.totalWords`
- two `strategySummaries`
- distinct chunk counts or average chunk sizes between strategies
