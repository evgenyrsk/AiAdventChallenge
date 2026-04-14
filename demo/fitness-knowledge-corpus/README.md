# Demo corpus знаний о фитнесе

Стабильный фитнес-ориентированный корпус для document indexing и retrieval-демо.

Этот корпус предназначен для:

- локальной индексации
- сравнения chunking
- проверки metadata
- retrieval-демо в Android chat app

## Что входит в корпус

- гайды по питанию
- заметки о тренировках и восстановлении
- короткие markdown-файлы в формате FAQ
- Kotlin-файл, похожий на исходный код, с доменной терминологией

## Рекомендуемые demo-запросы

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`
- `Как формируется weekly training plan?`

## Рекомендуемый smoke-запуск

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
```
