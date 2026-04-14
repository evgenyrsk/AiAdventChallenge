#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_NAME="${1:-fitness_knowledge}"

bash "$ROOT_DIR/scripts/document-indexing-smoke.sh" \
  "$ROOT_DIR/demo/fitness-knowledge-corpus" \
  "$SOURCE_NAME"
