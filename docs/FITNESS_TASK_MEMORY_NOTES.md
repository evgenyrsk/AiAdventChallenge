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

## Persistence

- scope: `per branch`
- storage: Room table `conversation_task_state`
- `main` и каждая ветка имеют свой snapshot
- при создании новой ветки state копируется из родительской ветки и дальше живет отдельно
