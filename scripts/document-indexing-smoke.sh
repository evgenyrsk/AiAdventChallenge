#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INPUT_CORPUS_PATH="${1:-demo/document-indexing-corpus}"
SOURCE_NAME="${2:-demo_corpus}"
SERVER_URL="${SERVER_URL:-http://localhost:8084}"

resolve_path() {
  local path="$1"
  local candidate="$path"

  if [[ "$candidate" != /* ]]; then
    candidate="$ROOT_DIR/$candidate"
  fi

  if [[ -d "$candidate" ]]; then
    (cd "$candidate" && pwd)
    return 0
  fi

  if [[ -f "$candidate" ]]; then
    local parent_dir
    parent_dir="$(cd "$(dirname "$candidate")" && pwd)"
    printf '%s/%s\n' "$parent_dir" "$(basename "$candidate")"
    return 0
  fi

  return 1
}

CORPUS_PATH="$(resolve_path "$INPUT_CORPUS_PATH")" || {
  printf 'Corpus path does not exist: %s\n' "$INPUT_CORPUS_PATH" >&2
  exit 1
}

echo "Using corpus: $CORPUS_PATH"
echo "Using source: $SOURCE_NAME"
echo "Using server: $SERVER_URL"

call_tool() {
  local method="$1"
  local payload="$2"
  local response

  response="$(curl -s "$SERVER_URL" \
    -H "Content-Type: application/json" \
    -d "$payload")"

  printf '%s\n' "$response"

  if [[ "$response" != *'"error":null'* ]]; then
    local error_code="unknown"
    local error_message="Unknown JSON-RPC error"

    if [[ "$response" =~ \"code\":(-?[0-9]+) ]]; then
      error_code="${BASH_REMATCH[1]}"
    fi

    if [[ "$response" =~ \"message\":\"([^\"]*)\" ]]; then
      error_message="${BASH_REMATCH[1]}"
    fi

    printf 'Smoke step failed: method=%s source=%s path=%s code=%s message=%s\n' \
      "$method" "$SOURCE_NAME" "$CORPUS_PATH" "$error_code" "$error_message" >&2
    exit 1
  fi
}

call_tool "index_documents" "$(cat <<JSON
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

call_tool "get_index_stats" "$(cat <<JSON
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

call_tool "compare_chunking_strategies" "$(cat <<JSON
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

call_tool "list_indexed_documents" "$(cat <<JSON
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
