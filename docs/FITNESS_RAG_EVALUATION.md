# Fitness RAG Evaluation

Этот файл предназначен для фиксации сравнения `PLAIN_LLM` vs `RAG_BASIC` vs `RAG_ENHANCED` по 10 контрольным вопросам.

Источники вопросов:

- `docs/FITNESS_RAG_QUESTIONS.md`
- `demo/fitness-knowledge-corpus/fixtures/rag_questions.json`

## Как заполнять

Есть два способа:

1. Вручную через Android chat UI
2. Через отдельный evaluation runner

## Evaluation runner

Runner читает `rag_questions.json`, делает по каждому вопросу:

- обычный LLM вызов без retrieval
- `RAG_BASIC`-вызов с retrieval без rewrite/post-processing
- `RAG_ENHANCED`-вызов с rewrite + filtering/reranking через `Document Index Server`
- сохраняет JSON и Markdown отчёт

Команда запуска:

```bash
AI_API_KEY=... ./gradlew :mcp-server:runFitnessRagEvaluation
```

Опциональные env-параметры:

- `AI_BASE_URL`
- `AI_MODEL`
- `DOCUMENT_INDEX_SERVER_URL`
- `RAG_SOURCE`
- `RAG_STRATEGY`
- `RAG_TOP_K`
- `RAG_ENHANCED_TOP_K_BEFORE`
- `RAG_ENHANCED_TOP_K_AFTER`
- `RAG_ENHANCED_THRESHOLD`
- `RAG_MAX_CHARS`
- `RAG_PER_DOCUMENT_LIMIT`
- `RAG_EVAL_JSON`
- `RAG_EVAL_MARKDOWN`

По умолчанию результаты пишутся в:

- `output/fitness-rag-evaluation/results.json`
- `output/fitness-rag-evaluation/report.md`

## Ручной режим

1. Пересобрать индекс по source `fitness_knowledge`
2. В Android чате задать вопрос в режиме `Обычный`
3. Переключить чат в режим `RAG Basic`
4. Задать тот же вопрос снова
5. Переключить чат в режим `RAG Enhanced`
6. Зафиксировать rewrite, кандидатов до/после фильтрации, ответ, источники и краткий вывод

## Шаблон сравнения

| ID | Вопрос | Без RAG | RAG Basic | RAG Enhanced | Итог |
| --- | --- | --- | --- | --- | --- |
| q01 | Что важнее для похудения: дефицит калорий или время приёма пищи? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Оценить полноту, шум и groundedness |
| q02 | Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Оценить наличие диапазона `1.6-2.2 г/кг` |
| q03 | Что важнее новичку: идеальный сплит или регулярность тренировок? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить акцент на регулярности |
| q04 | Помогает ли увеличение шагов повышать общий расход энергии? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить связь шагов с расходом энергии |
| q05 | Почему сон влияет на восстановление и контроль аппетита? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить связь сна с аппетитом и качеством тренировки |
| q06 | Нужно ли каждый раз тренироваться до отказа для роста мышц? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить отсутствие категоричного "да" |
| q07 | Сколько тренировок в неделю достаточно новичку? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить наличие диапазона `2-4` |
| q08 | Можно ли худеть без кардио? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить акцент на дефиците калорий |
| q09 | Почему жидкие калории могут мешать снижению веса? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить тезис о низкой насыщаемости |
| q10 | Что делать, если по базе знаний на вопрос нет достаточного ответа? | Заполнить во время прогона | Зафиксировать answer/sources | Зафиксировать rewrite/sources | Проверить честное признание нехватки данных |
