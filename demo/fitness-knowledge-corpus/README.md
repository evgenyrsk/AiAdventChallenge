# Fitness Knowledge Corpus Layout

Этот каталог больше не индексируется целиком как knowledge base.

## Структура

- `content/`
  indexable fitness knowledge base для source `fitness_knowledge`
- `fixtures/`
  machine-readable сценарии и вопросы для evaluation runner'ов
- `support/`
  служебные corpus-adjacent материалы, которые не должны попадать в retrieval index

## Каноническая индексация

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus/content fitness_knowledge
```

или

```bash
bash scripts/reindex-fitness-knowledge.sh
```

Project/demo инструкции смотри в `docs/`, а не в indexable corpus.
