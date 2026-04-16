# Local Reranker Service

This sidecar hosts a local cross-encoder reranker for the fitness RAG pipeline.

## Default model

- `BAAI/bge-reranker-base`

## Endpoints

- `GET /health`
- `POST /rerank`

## Local run

```bash
cd tools/reranker-service
./run.sh
```

Environment variables:

- `RERANKER_MODEL`
- `RERANKER_PORT`
- `RERANKER_MAX_CANDIDATES`

## Request shape

```json
{
  "query": "protein intake during fat loss",
  "candidates": [
    {
      "chunkId": "chunk-1",
      "text": "1.6-2.2 g/kg protein is a practical range...",
      "title": "protein_guide.md",
      "relativePath": "nutrition/protein_guide.md",
      "section": "Practical intake ranges",
      "retrievalScore": 0.91,
      "semanticScore": 0.88,
      "keywordScore": 1.0
    }
  ],
  "top_k_after": 4,
  "min_score_threshold": 0.2,
  "timeout_ms": 3500
}
```

The Kotlin `mcp-server` calls this sidecar internally. Android does not talk to it directly.
