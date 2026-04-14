# Document Indexing Demo Script

Пошаговый сценарий демонстрации document indexing и retrieval integration.

## 1. Подготовка

Убедись, что:

- проект открыт в Android Studio
- есть Android эмулятор
- в `local.properties` задан `AI_API_KEY`
- ты находишься в корне проекта

Если хочешь, чтобы Android app без доп. настройки использовал этот индекс, source должен быть `local_docs`.

## 2. Запуск MCP серверов

В отдельном терминале:

```bash
./gradlew :mcp-server:runMultiServer
```

Что сказать:

- “Я поднимаю все MCP серверы, включая новый document index server на `8084`.”
- “Android эмулятор будет ходить к ним через `10.0.2.2`.”

Ожидаемый результат:

- в логах появятся 4 сервера
- среди них `Document Index: http://localhost:8084`

## 3. Индексация demo corpus

Во втором терминале:

```bash
bash scripts/document-indexing-smoke.sh demo/document-indexing-corpus local_docs
```

Что сказать:

- “Сейчас я индексирую стабильный локальный корпус документов.”
- “Он содержит markdown, txt, code и XML, а PDF parsing в проекте отдельно покрыт integration test.”

Если нужен более продуктовый demo для fitness-assistant сценария, вместо этого можно использовать:

```bash
bash scripts/document-indexing-smoke.sh demo/fitness-knowledge-corpus local_docs
```

Что должно произойти:

- вызовится `index_documents`
- затем `get_index_stats`
- затем `compare_chunking_strategies`
- затем `list_indexed_documents`

## 4. Что показать после индексации

Покажи в output или проговори:

- `successfulDocuments`
- `corpusStats.documentCount`
- `corpusStats.totalCharacters`
- `corpusStats.totalWords`
- наличие двух стратегий:
  - `fixed_size`
  - `structure_aware`

Что сказать:

- “Индекс создается локально.”
- “Для каждого чанка генерируются embeddings и metadata.”
- “Две стратегии chunking реально запускаются и дают разные summary.”

## 5. Показать артефакты

В терминале можно быстро показать:

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

Что важно отметить:

- SQLite index:
  - `mcp-server/output/document-index/document_index.db`
- JSON exports:
  - `local_docs_fixed_size_index.json`
  - `local_docs_structure_aware_index.json`
- acceptance report:
  - `local_docs_indexing_report.json`

Если хочешь показать metadata прямо глазами:

```bash
sed -n '1,80p' mcp-server/output/document-index/export/local_docs_structure_aware_index.json
```

Что сказать:

- “Здесь видно `chunk_id`, `source`, `title`, `section`, `chunking_strategy` и другие metadata.”

## 6. Показать сравнение стратегий

Если хочешь отдельно вызвать comparison вручную:

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":10,
    "method":"compare_chunking_strategies",
    "params":{
      "source":"local_docs",
      "path":"'"$(pwd)"'/demo/document-indexing-corpus"
    }
  }'
```

Что сказать:

- “`fixed_size` полезен как baseline с предсказуемым размером chunks.”
- “`structure_aware` лучше сохраняет смысловые границы и удобнее для retrieval и citations.”

## 7. Запуск Android приложения

Теперь в Android Studio:

- запускаешь app на эмуляторе
- открываешь экран чата

Перед началом можно проговорить:

- “Теперь я покажу, что индекс не просто существует, а реально используется приложением.”

## 8. Knowledge retrieval demo в Android

Введи по очереди 2–3 запроса.

Хорошие запросы:

- `Объясни по документации, зачем нужны metadata у chunk'ов`
- `Чем fixed_size chunking отличается от structure_aware?`
- `Как устроен retrieval pipeline в проекте?`
- `Какой storage используется для document index?`

Если индексирован fitness corpus, лучше использовать:

- `Сколько белка нужно при наборе массы?`
- `Что такое дефицит калорий?`
- `Что лучше для новичка: full body или upper lower?`
- `Почему сон важен для восстановления?`

Что должно произойти:

- Android app распознает knowledge/document query
- вызывает `answer_with_retrieval`
- получает retrieval package с контекстом
- добавляет его в LLM prompt
- показывает `Knowledge Base Context` card

Что показать на экране:

- сам ответ модели
- карточку `Knowledge Base Context`
- `source`
- `strategy`
- найденные source chunks

Что сказать:

- “Это уже интеграция в реальный chat flow.”
- “Приложение использует локальный индекс через MCP, а не отдельный скрипт вне продукта.”

## 9. Опциональный retrieval demo с терминала

Если хочешь отдельно показать retrieval без Android:

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":11,
    "method":"retrieve_relevant_chunks",
    "params":{
      "query":"What is the difference between fixed size and structure aware chunking?",
      "source":"local_docs",
      "strategy":"structure_aware",
      "topK":3,
      "maxChars":1500
    }
  }'
```

Или:

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":12,
    "method":"answer_with_retrieval",
    "params":{
      "query":"Explain why chunk metadata is important",
      "source":"local_docs",
      "strategy":"structure_aware",
      "topK":3,
      "maxChars":1500
    }
  }'
```

## 10. Финальная фраза demo

Можно завершить так:

- “Система локально индексирует документы, режет их двумя стратегиями, строит embeddings, сохраняет индекс в SQLite, сравнивает стратегии и отдает retrieval через MCP.”
- “Дальше этот индекс уже используется Android приложением для knowledge-aware ответов.”

## 11. Ограничение текущего demo

Через текущее Android приложение удобно демонстрировать `retrieval/use of index`, но не сам запуск `index_documents` из UI. Индексацию сейчас лучше показывать через MCP/terminal, а потом уже использовать результат в app.

## 12. Важное замечание про устройство

Если demo идет не на эмуляторе, а на физическом Android устройстве:

- сейчас `MultiServerConfig` использует `10.0.2.2`, это адрес для эмулятора
- для физического устройства нужно будет подставить IP твоего хоста в локальной сети
