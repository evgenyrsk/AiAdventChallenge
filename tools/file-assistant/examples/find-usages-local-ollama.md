# Example: Find Usages

```bash
./gradlew :file-assistant-tool:run --args="find-usages LocalOllamaClient"
```

Expected result:
- deterministic search across safe project files
- grouped usage report
- files and line numbers
- no file changes
