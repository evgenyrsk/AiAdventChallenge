# Document Indexing Acceptance

The assignment is considered complete when the following can be shown on one local run.

## Required proof

1. `index_documents` completes on the demo corpus.
2. `corpusStats` is present in the response and reports non-zero:
   - document count
   - total characters
   - total words
3. Two strategies are executed:
   - `fixed_size`
   - `structure_aware`
4. Strategy summaries differ by chunk distribution.
5. Chunks contain required metadata:
   - `chunk_id`
   - `source`
   - `title`
   - `section`
   - `chunking_strategy`
6. SQLite index exists.
7. JSON exports exist, including:
   - per-strategy chunk export
   - indexing report export

## Suggested verification flow

1. Start the document index MCP server.
2. Call `index_documents`.
3. Call `get_index_stats`.
4. Call `compare_chunking_strategies`.
5. Optionally call `list_indexed_documents`.

## Expected artifacts

- SQLite DB: `mcp-server/output/document-index/document_index.db`
- Chunk exports:
  - `local_docs_fixed_size_index.json`
  - `local_docs_structure_aware_index.json`
- Acceptance report:
  - `local_docs_indexing_report.json`

## Automated verification

Use:

```bash
./gradlew :mcp-server:test
```

The integration test verifies mixed-document indexing, PDF parsing, metadata presence, strategy comparison, report generation and retrieval-readiness.
