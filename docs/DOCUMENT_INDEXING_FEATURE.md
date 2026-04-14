# Document Indexing Feature

This feature provides a local document indexing foundation for retrieval and
future RAG-style workflows.

## What is implemented

- mixed document loading for markdown, text, code and PDF
- two chunking strategies:
  - fixed size
  - structure aware
- pluggable embeddings provider abstraction
- local SQLite storage for chunks, embeddings and metadata
- JSON export for inspection and debugging
- MCP tools for indexing, stats, comparison and retrieval

## Main MCP tools

- `index_documents`
- `reindex_documents`
- `get_index_stats`
- `compare_chunking_strategies`
- `list_indexed_documents`
- `search_index`
- `retrieve_relevant_chunks`
- `answer_with_retrieval`

## Recommended local demo

1. Start the server:

```bash
./gradlew :mcp-server:runDocumentIndexServer
```

2. Run the smoke flow:

```bash
bash scripts/document-indexing-smoke.sh
```

3. Inspect artifacts:

- `mcp-server/output/document-index/document_index.db`
- `mcp-server/output/document-index/export/`

## Acceptance evidence

The feature is considered complete for the assignment when one local run shows:

- indexed mixed corpus
- generated embeddings
- stored metadata per chunk
- separate results for `fixed_size` and `structure_aware`
- comparison output
- local index persisted in SQLite

See also:

- `docs/DOCUMENT_INDEXING_CORPUS.md`
- `docs/FITNESS_KNOWLEDGE_CORPUS.md`
- `docs/FITNESS_DEMO_SCRIPT.md`
- `docs/FITNESS_VIDEO_DEMO.md`
- `docs/DOCUMENT_INDEXING_DEMO.md`
- `docs/DOCUMENT_INDEXING_DEMO_SCRIPT.md`
- `docs/DOCUMENT_INDEXING_RESULT_DEMO.md`
- `docs/DOCUMENT_INDEXING_ACCEPTANCE.md`
