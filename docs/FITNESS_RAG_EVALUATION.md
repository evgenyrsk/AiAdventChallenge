# Fitness RAG Evaluation

Этот файл предназначен для фиксации сравнения `PLAIN_LLM` vs `RAG_BASIC` vs `RAG_ENHANCED` по 11 контрольным вопросам.

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
- для RAG-ответов сохраняет детерминированные `sources` и `quotes` из финальных chunks
- перед генерацией применяет anti-hallucination gate
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

## Long dialog runner

Для production-like multi-turn `RAG_ENHANCED` есть отдельный Gradle task:

```bash
AI_API_KEY=... ./gradlew :mcp-server:runFitnessLongDialogEvaluation
```

Он:

- читает structured fixtures из `demo/fitness-knowledge-corpus/fixtures/long_dialog_scenarios.json`
- прогоняет 2 длинных сценария без Android UI
- использует shared `task memory` logic из `rag-core`
- проверяет `goalPreserved`, `constraintsPreserved`, `hasSources`
- пишет Markdown report по умолчанию в:
  - `output/fitness-long-dialog-evaluation/report.md`

Опциональные env-параметры:

- `RAG_LONG_DIALOG_FIXTURES_PATH`
- `RAG_LONG_DIALOG_EVAL_MARKDOWN`

## Ручной режим

1. Пересобрать индекс по source `fitness_knowledge`
2. В Android чате задать вопрос в режиме `Обычный`
3. Переключить чат в режим `RAG Basic`
4. Задать тот же вопрос снова
5. Переключить чат в режим `RAG Enhanced`
6. Зафиксировать rewrite, кандидатов до/после фильтрации, ответ, источники и краткий вывод

## Long dialog evaluation

Для production-like mini-chat дополнительно нужно прогонять 2 длинных сценария из:

- `docs/FITNESS_LONG_DIALOG_SCENARIOS.md`

На каждом шаге фиксировать:

- `taskStateBefore`
- `taskStateAfter`
- `retrievalQuery`
- `rewrittenQuery`
- `retrievedSources`
- `finalAnswer`
- `hasSources`
- `goalPreserved`
- `constraintsPreserved`
- `notes`

## Что теперь проверяется на 11 вопросах

Для каждого вопроса в отчёте должны быть видны:

- `actual answer`
- `actual sources`
- `actual quotes`
- `hasSources`
- `hasQuotes`
- `answerGroundedInQuotes`
- `fallbackTriggered`
- `fallbackExpected`
- `fallbackAppropriate`
- `notes`

Ожидаемое поведение:

- у grounded ответов есть `sources` и `quotes`
- `sources` и `quotes` строятся только из финальных retrieved chunks
- при слабой релевантности система возвращает честный fallback вместо догадки
- у специального low-relevance кейса `q11` ожидаются `fallbackTriggered = true`, `sources = []`, `quotes = []`

## Practical grounded mode

Текущий `RAG_ENHANCED` нужно интерпретировать как practical grounded mode, а не как formal proof system.

Что это означает:

- assistant инструктируется отвечать только на основе retrieved context
- anti-hallucination / answerability gate переводит слабый retrieval в честный fallback
- `sources` и `quotes` всегда retrieval-derived
- quality groundedness подтверждается через evaluation reports

Что это не означает:

- система не доказывает формально невозможность любого выхода LLM за пределы контекста
- runtime не использует дополнительные heuristic post-generation verifier'ы поверх текста ответа

На текущем этапе acceptance опирается на retrieval-first архитектуру, grounded evidence и воспроизводимые evaluation-сценарии.

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
| q11 | Как принимать креатин моногидрат: нужна ли фаза загрузки и сколько граммов в день? | Заполнить во время прогона | Ожидается fallback без источников и цитат | Ожидается `не знаю` после anti-hallucination gate | Проверить low-relevance сценарий и просьбу уточнить вопрос |
