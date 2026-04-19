# Корпус знаний о фитнесе

Рекомендуемый фитнес-ориентированный корпус для тестирования функциональности document indexing.

## Расположение

`demo/fitness-knowledge-corpus/`

## Почему стоит использовать

- соответствует домену приложения: фитнес, питание, восстановление
- помогает нагляднее показать ценность retrieval в Android app
- indexable knowledge docs теперь отделены от fixtures и служебных материалов
- хорошо подходит для сравнения chunking и проверки metadata

## Рекомендуемая команда индексации

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus/content fitness_knowledge
```

Скрипт сам приводит путь корпуса к абсолютному, поэтому пример работает независимо от того,
из какого подпроекта был запущен document index server.

## Layout

- `content/` — каноническая knowledge base для индексации
- `fixtures/` — structured evaluation assets для runner'ов
- `support/` — corpus-adjacent материалы, которые не должны попадать в retrieval index
- root-level `README.md` / `SEED_MANIFEST.md` — навигация и описание структуры, а не knowledge source

## Подходящие demo-вопросы

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`
- `Как формируется weekly training plan?`

## Ожидаемая ценность

Этот корпус делает retrieval-демо более продуктовым, потому что приложение
может отвечать на фитнес-вопросы, опираясь на локально проиндексированные
документы, а не только на технические материалы проекта.
