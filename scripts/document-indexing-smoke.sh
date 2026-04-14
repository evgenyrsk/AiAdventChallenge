#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORPUS_PATH="${1:-$ROOT_DIR/demo/document-indexing-corpus}"
SOURCE_NAME="${2:-demo_corpus}"
SERVER_URL="${SERVER_URL:-http://localhost:8084}"

echo "Using corpus: $CORPUS_PATH"
echo "Using source: $SOURCE_NAME"
echo "Using server: $SERVER_URL"

call_tool() {
  local payload="$1"
  curl -s "$SERVER_URL" \
    -H "Content-Type: application/json" \
    -d "$payload"
  printf '\n'
}

call_tool "$(cat <<JSON
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "index_documents",
  "params": {
    "path": "$CORPUS_PATH",
    "source": "$SOURCE_NAME",
    "strategies": ["fixed_size", "structure_aware"]
  }
}
JSON
)"

call_tool "$(cat <<JSON
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "get_index_stats",
  "params": {
    "source": "$SOURCE_NAME"
  }
}
JSON
)"

call_tool "$(cat <<JSON
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "compare_chunking_strategies",
  "params": {
    "source": "$SOURCE_NAME",
    "path": "$CORPUS_PATH"
  }
}
JSON
)"

call_tool "$(cat <<JSON
{
  "jsonrpc": "2.0",
  "id": 4,
  "method": "list_indexed_documents",
  "params": {
    "source": "$SOURCE_NAME"
  }
}
JSON
)"
