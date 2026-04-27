#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-/tmp/private-ai-gradle}"
PRIVATE_AI_SERVICE_BASE_URL="${PRIVATE_AI_SERVICE_BASE_URL:-http://localhost:8085}"
PRIVATE_AI_API_KEY="${PRIVATE_AI_API_KEY:-}"
PRIVATE_AI_SERVICE_MODEL="${PRIVATE_AI_SERVICE_MODEL:-qwen2.5:3b-instruct}"

export JAVA_HOME
export GRADLE_USER_HOME
export PRIVATE_AI_SERVICE_BASE_URL
export PRIVATE_AI_API_KEY
export PRIVATE_AI_SERVICE_MODEL

cd "$ROOT_DIR"
./gradlew :private-ai-service:runSmoke
