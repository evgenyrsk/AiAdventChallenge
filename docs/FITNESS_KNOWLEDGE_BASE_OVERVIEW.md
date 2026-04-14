# Fitness Knowledge Base Overview

## Где лежит база знаний

Основной корпус для RAG находится в `demo/fitness-knowledge-corpus/`.

Рекомендуемый source для индексации: `fitness_knowledge`.

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

- `nutrition/nutrition_basics.md`
- `nutrition/protein_guide.md`
- `nutrition/calorie_balance.md`
- `training/beginner_strength_training.md`
- `training/recovery_sleep_steps.md`
- `nutrition/fat_loss_myths.md`
- `nutrition/muscle_gain_basics.md`
- `training/workout_frequency.md`
- `nutrition/hydration_and_habits.md`
- `faq/fitness_faq.md`

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
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus fitness_knowledge
```
