# Fitness Task Memory Notes

## Что хранится

`ConversationTaskState` хранит компактную рабочую память по ветке:

- `dialogGoal`
- `resolvedConstraints`
- `definedTerms`
- `userClarifications`
- `openQuestions`
- `latestSummary`
- `updatedAt`

## Чего там нет

- полного transcript
- retrieved chunks
- `sources` / `quotes`
- знаний, не подтвержденных retrieval

## Где используется

1. `ProcessChatTurnUseCase`
   - читает state по `branchId`
   - обновляет его до retrieval
   - сохраняет обратно после user turn

2. `PrepareRagRequestUseCase`
   - берет `dialogGoal`, `retrievalHints`, `latestSummary`
   - добавляет их в retrieval input как dialog hints

3. `RagPromptBuilder`
   - добавляет compact block `Conversation Task State`
   - напоминает модели, что память задачи не является доказательной базой

## Важное архитектурное правило

`task memory` помогает не терять цель и ограничения, но:

- не отображается как `source`
- не участвует в `Grounded Sources`
- не подменяет retrieved evidence

## Grounded Runtime Note

Текущий chat runtime специально не использует дополнительные ручные или heuristic post-generation проверки текста ответа.

Вместо этого grounded behavior обеспечивается комбинацией:

- retrieval-first prompt assembly
- answerability / fallback policy
- retrieval-derived `sources` и `quotes`
- evaluation runner'ов, которые позволяют проверять groundedness и fallback на воспроизводимых сценариях

Это уменьшает риск хрупких корнер-кейсов в runtime и сохраняет архитектуру простой и предсказуемой.

## Persistence

- scope: `per branch`
- storage: Room table `conversation_task_state`
- `main` и каждая ветка имеют свой snapshot
- при создании новой ветки state копируется из родительской ветки и дальше живет отдельно
