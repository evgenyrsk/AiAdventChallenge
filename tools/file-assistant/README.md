# File Assistant Tooling

Developer file-ops assistant for this repository. It is intentionally separate from the Android fitness chat runtime.

Русскоязычная инструкция для демонстрации: [DEMO_RU.md](DEMO_RU.md).

The tool can:
- read safe project files
- search across safe project files
- analyze usage locations
- create markdown documentation/ADR files
- preview or apply changes
- return a diff

It does not modify `ChatScreen`, `ChatViewModel`, or user-facing fitness assistant flows.

## Commands

Find usages:

```bash
./gradlew :file-assistant-tool:run --args="find-usages LocalOllamaClient"
```

Find usages from a high-level goal:

```bash
./gradlew :file-assistant-tool:run --args="--goal 'Найди все места, где используется LocalOllamaClient'"
```

Generate ADR dry-run:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr private-ai-service --dry-run"
```

Generate ADR from a high-level goal:

```bash
./gradlew :file-assistant-tool:run --args="--goal 'Сгенерируй ADR по архитектуре private AI service' --dry-run"
```

Apply ADR creation:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr private-ai-service --apply"
git diff
```

JSON output:

```bash
./gradlew :file-assistant-tool:run --args="find-usages LocalOllamaClient --json"
```

## Output

Markdown CLI output uses this structure:

```markdown
# File Assistant Result

## Goal
...

## Summary
...

## Files Read
...

## Files Changed
...

## Diff
...

## Warnings
...
```

With `--json`, the same result is emitted as:

```json
{
  "goal": "...",
  "status": "SUCCESS",
  "filesRead": [],
  "filesChanged": [],
  "summary": "...",
  "diff": "...",
  "warnings": [],
  "nextSteps": []
}
```

## Safety Model

All file operations go through `ProjectFileTools`.

Rules:
- paths must be relative to the project root
- absolute paths are denied
- `..` path traversal is denied
- canonical paths must remain inside the project root, so symlinks outside the repo are denied
- reads and writes are size-limited
- writes default to dry-run and require `--apply` for real changes
- writes are limited to documentation/tooling paths for v1

Forbidden paths include:
- `.git`
- `.gradle`
- `.idea`
- `build`
- `generated`
- `node_modules`
- configured output directories

Allowed extensions:
- `.kt`
- `.kts`
- `.md`
- `.json`
- `.yaml`
- `.yml`
- `.xml`
- `.txt`

Secret-like files such as `.env`, `local.properties`, `secrets.properties`, and `keystore.properties` are denied.

## LLM Backend

File operations are deterministic and do not require an LLM.

Optional environment variables:

```bash
export FILE_ASSISTANT_LLM_BACKEND=private # private|ollama|cloud|none
export FILE_ASSISTANT_BASE_URL=http://localhost:8080
export FILE_ASSISTANT_API_KEY=...
export FILE_ASSISTANT_MODEL=...
```

In v1, the assistant keeps file reads/writes deterministic. If the backend is unset or unavailable, `find-usages` still works and `generate-adr` produces a structured deterministic draft.

## Reproducibility

Run tests:

```bash
./gradlew :file-assistant-tool:test
```

Check Android runtime compilation:

```bash
./gradlew :app:compileDebugKotlin
```

Dry-run commands do not change files:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr file-assistant --dry-run"
git diff
```

Apply commands create reviewable diffs:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr file-assistant --apply"
git diff
```
