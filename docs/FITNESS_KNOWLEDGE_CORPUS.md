# Fitness Knowledge Corpus

Recommended fitness-specific corpus for testing the document indexing feature.

## Location

`demo/fitness-knowledge-corpus/`

## Why use it

- aligned with the app domain: fitness, nutrition, recovery
- easier to demonstrate retrieval value in the Android app
- includes markdown, text and code
- good fit for chunking comparison and metadata inspection

## Recommended indexing command

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
```

## Good demo questions

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`
- `Как формируется weekly training plan?`

## Expected value

This corpus makes the retrieval demo feel product-relevant because the app can
answer fitness-specific questions using indexed local documents instead of only
technical project materials.
