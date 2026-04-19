# Fitness Knowledge Seed Manifest

Этот corpus содержит явный seed-набор документов для RAG-демо фитнес-ассистента.
Индексируемой knowledge base считается только каталог `content/`.

## Назначение

- индексироваться локально через `Document Index Server`
- давать стабильные секции для `structure_aware` chunking
- покрывать контрольные вопросы по питанию, тренировкам, восстановлению, шагам и бытовым привычкам

## Канонические документы для RAG v1

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

Документы являются статическими seed-файлами, чтобы corpus был прозрачен, читаем в git и воспроизводим при локальной пересборке индекса.
