# Fitness Knowledge Seed Manifest

Этот корпус является явным seed-набором документов для RAG-демо фитнес-ассистента.

## Назначение

- индексироваться локально через `Document Index Server`
- давать стабильные секции для `structure_aware` chunking
- покрывать контрольные вопросы по питанию, тренировкам, восстановлению, шагам и бытовым привычкам

## Канонические документы для RAG v1

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

Документы являются статическими seed-файлами, чтобы corpus был прозрачен, читаем в git и воспроизводим при локальной пересборке индекса.
