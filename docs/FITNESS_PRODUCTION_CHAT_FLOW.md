# Fitness Production Chat Flow

## Turn Flow

Для `RAG_ENHANCED` один ход теперь проходит так:

1. пользователь отправляет новое сообщение
2. `ProcessChatTurnUseCase` читает branch history
3. загружается `ConversationTaskState` по `branchId`
4. `TaskStateUpdater` обновляет goal / constraints / clarifications / summary
5. обновленный state сохраняется в persistence
6. `PrepareRagRequestUseCase` строит retrieval input с учетом task memory
7. existing enhanced RAG pipeline делает rewrite + retrieval + filtering/reranking + anti-hallucination gate
8. `ChatMessageHandlerImpl` генерирует grounded answer
9. если retrieval признан недостаточным, система возвращает честный fallback вместо догадки
10. UI показывает clean answer text, retrieval details и developer-only `Task Memory`

## Separation Of Concerns

- `ChatMessage` = transcript
- `ConversationTaskState` = conversational memory
- `RetrievalSummary` = retrieval/debug payload
- `GroundedAnswerPayload` = evidence-backed answer metadata

## Grounding Guarantee

Текущий flow реализует retrieval-first grounded mode:

- prompt требует отвечать только по retrieved context
- answerability gate ограничивает ответы при слабом retrieval
- `sources` и `quotes` остаются привязаны только к retrieved chunks

При этом runtime не использует отдельный heuristic verifier поверх финального текста ответа.
Поэтому систему стоит описывать как practical grounded RAG с honest fallback, а не как формальную гарантию невозможности галлюцинаций.

## UI visibility

В Android debug details sheet теперь видны:

- query pipeline
- grounded answer
- grounded sources
- grounded quotes
- `Task Memory`

Это позволяет проверять длинные сценарии без превращения основного чата в перегруженный dashboard.
