# Example: Generate ADR Dry Run

```bash
./gradlew :file-assistant-tool:run --args="generate-adr private-ai-service --dry-run"
```

Expected result:
- context collected from source and docs
- preview diff for `docs/adr/NNNN-private-ai-service.md`
- no file changes until `--apply` is passed
