#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_DIR="${SCRIPT_DIR}/.venv"

python3 -m venv "${VENV_DIR}"
source "${VENV_DIR}/bin/activate"
pip install --upgrade pip
pip install -r "${SCRIPT_DIR}/requirements.txt"

export RERANKER_MODEL="${RERANKER_MODEL:-BAAI/bge-reranker-base}"
export RERANKER_MAX_CANDIDATES="${RERANKER_MAX_CANDIDATES:-24}"

exec uvicorn app:app --app-dir "${SCRIPT_DIR}" --host 0.0.0.0 --port "${RERANKER_PORT:-8091}"
