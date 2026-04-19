# Fitness Knowledge Base Overview

## Где лежит база знаний

Индексируемая knowledge base для RAG находится в `demo/fitness-knowledge-corpus/content/`.

Рекомендуемый source для индексации: `fitness_knowledge`.

Structured fixtures и навигационные файлы живут рядом в `demo/fitness-knowledge-corpus/`, но не считаются частью retrieval corpus.

## Что входит в corpus v1

- питание и энергетический баланс
- белок и сохранение мышц
- силовые тренировки для новичка
- восстановление, сон и шаги
- мифы о похудении
- базовые принципы набора массы
- гидратация и поведенческие привычки
- FAQ для быстрых прикладных вопросов

## Канонические документы

- `content/nutrition/nutrition_basics.md`
- `content/nutrition/protein_guide.md`
- `content/nutrition/calorie_balance.md`
- `content/training/beginner_strength_training.md`
- `content/training/recovery_sleep_steps.md`
- `content/nutrition/fat_loss_myths.md`
- `content/nutrition/muscle_gain_basics.md`
- `content/training/workout_frequency.md`
- `content/nutrition/hydration_and_habits.md`
- `content/faq/fitness_faq.md`

## Почему corpus подходит для RAG

- документы размечены заголовками и секциями, поэтому `structure_aware` chunking выдаёт понятные section labels
- внутри есть конкретные формулировки под retrieval и контрольные вопросы
- советы базовые и безопасные, без экстремальных и медицинских утверждений
- corpus не смешан с технической документацией проекта, поэтому retrieval выдаёт более доменно-релевантные chunks

## Пересборка индекса

```bash
bash scripts/reindex-fitness-knowledge.sh
```

или

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus/content fitness_knowledge
```
