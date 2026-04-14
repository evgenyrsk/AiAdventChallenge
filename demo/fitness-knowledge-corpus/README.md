# Fitness Knowledge Demo Corpus

Stable fitness-specific corpus for document indexing and retrieval demos.

This corpus is designed for:

- local indexing
- chunking comparison
- metadata inspection
- retrieval demos in the Android chat app

## Included materials

- nutrition guides
- workout and recovery notes
- short FAQ-style markdown
- source-like Kotlin file with domain terminology

## Recommended demo queries

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`
- `Как формируется weekly training plan?`

## Recommended smoke run

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
```
