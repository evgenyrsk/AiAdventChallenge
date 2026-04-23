# Local RAG Demo Guide

Практическая инструкция для демонстрации реализованного сценария:

- локальный retrieval через existing document-index MCP server
- генерация ответа через `LOCAL_OLLAMA`
- отображение источников прямо в existing chat UI
- сравнение `local vs cloud`
- запуск минимального benchmark/evaluation сценария

## Что демонстрируем

К концу demo должно быть видно, что приложение умеет:

- отвечать в existing Android chat flow без отдельного RAG-экрана
- использовать existing индекс `fitness_knowledge`
- выполнять retrieval локально через existing document-index runtime
- генерировать ответ через Ollama
- показывать compact sources рядом с ответом ассистента
- показывать retrieval/debug details в bottom sheet
- сравнивать один и тот же RAG context для `LOCAL_OLLAMA` и cloud backend
- запускать быстрый benchmark по нескольким фиксированным вопросам

## Prerequisites

Перед показом должны быть доступны:

- Android Studio или Android SDK
- Android эмулятор или устройство
- Ollama на хост-машине
- локальный MCP/document-index server
- актуальный индекс `fitness_knowledge`

## Шаг 1. Поднять MCP server с document retrieval

В корне проекта:

```bash
./gradlew :mcp-server:runMultiServer
```

Что важно проверить:

- `Document Index Server` поднят на `localhost:8084`
- Android эмулятор сможет достучаться к нему через `10.0.2.2:8084`

Опциональная проверка:

```bash
curl -s http://localhost:8084 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"tools/list"
  }'
```

В ответе должны быть retrieval tools:

- `retrieve_relevant_chunks`
- `answer_with_retrieval`

## Шаг 2. Пересобрать или проверить индекс

Если индекс уже существует, можно просто проверить его наличие. Если нужно пересобрать:

```bash
bash scripts/reindex-fitness-knowledge.sh
```

После этого должны существовать:

```bash
ls mcp-server/output/document-index
ls mcp-server/output/document-index/export
```

Ожидаемые artefacts:

- `mcp-server/output/document-index/document_index.db`
- `fitness_knowledge_fixed_size_index.json`
- `fitness_knowledge_structure_aware_index.json`
- `fitness_knowledge_indexing_report.json`

## Шаг 3. Убедиться, что Ollama доступна

На хост-машине проверьте, что Ollama запущена и нужная модель присутствует:

```bash
ollama list
```

Для demo удобно использовать компактную instruct-модель, имя которой совпадает с тем, что указано в приложении, например:

- `qwen2.5:3b-instruct`
- или другая уже скачанная модель

Важно:

- для Android эмулятора `localhost` и `127.0.0.1` в приложении транслируются в `10.0.2.2`
- если модель не найдена, приложение вернет понятную ошибку от `LocalOllamaRepository`

## Шаг 4. Установить и открыть Android приложение

Если эмулятор уже запущен:

```bash
./gradlew :app:installDebug
```

После запуска приложения:

1. Откройте экран чата
2. Откройте настройки стратегии
3. В секции `AI Backend` выберите `Local Ollama`
4. Проверьте:
   - `Host`: обычно `10.0.2.2`, `localhost` или `127.0.0.1`
   - `Port`: обычно `11434`
   - `Model`: точное имя из `ollama list`
5. Примените настройки

## Шаг 5. Включить local RAG

На экране чата:

1. Выберите режим `RAG Enhanced`
2. Убедитесь, что backend в заголовке показывает локальную модель

Именно в этом сценарии демонстрируется:

- retrieval через local document-index runtime
- generation через `LOCAL_OLLAMA`
- grounded answer с compact sources

## Шаг 6. Задать канонический вопрос

Рекомендуемый вопрос для demo:

```text
Почему сон влияет на восстановление и контроль аппетита?
```

Дополнительные хорошие вопросы:

- `Что важнее для похудения: дефицит калорий или время приема пищи?`
- `Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?`
- `Почему жидкие калории могут мешать снижению веса?`

## Шаг 7. Что должно быть видно в основном chat UI

После ответа ассистента проверьте, что в existing chat flow появились:

- обычный assistant message bubble
- компактный badge вида `Ollama RAG`
- latency в миллисекундах
- блок `Источники` под assistant message

Ожидаемое поведение:

- ответ отображается как обычное сообщение ассистента
- источники показываются компактно, без перегрузки UI
- основной текст ответа не загрязнен техническим retrieval/debug выводом

## Шаг 8. Открыть retrieval details

Под полем ввода должен быть виден `Knowledge Base Context` / `RetrievalSummaryCard`.

Нажмите `Детали`.

В bottom sheet должны быть доступны:

- `Query`
- `Pipeline`
- `Task Memory`, если есть накопленный state
- `Grounded Answer`
- `Grounded Sources`
- `Grounded Quotes`
- `Final Context`
- `Initial Candidates`
- `Filtered Out`

Это подтверждает, что:

- retrieval реально сработал
- rewrite и post-processing применились
- источники связаны с retrieved chunks, а не выдуманы моделью

## Шаг 9. Показать compare flow

В том же details sheet нажмите:

```text
Compare local vs cloud
```

Что делает этот сценарий:

- берет тот же `question`
- готовит один `PreparedRagRequest`
- использует один и тот же retrieval context
- отдельно прогоняет generation через:
  - `LOCAL_OLLAMA`
  - `REMOTE`

Что должно появиться в details sheet:

- секция `Local vs Cloud`
- latency для local и cloud
- статусы `ok/error`
- текст local ответа
- текст cloud ответа

Это и есть основной практический compare flow для demo.

## Шаг 10. Показать benchmark / evaluation flow

В том же details sheet нажмите:

```text
Run benchmark
```

Что делает benchmark:

- прогоняет несколько фиксированных RAG-вопросов
- для каждого вопроса вызывает compare flow
- считает:
  - сколько кейсов успешно прошло у обоих backends
  - среднюю latency local
  - среднюю latency cloud

Что должно появиться:

- секция `Benchmark Summary`
- `Samples`
- `Both succeeded`
- `Avg local latency`
- `Avg cloud latency`
- список коротких результатов по вопросам

Это не research framework, а демонстрационный practical benchmark.

## Что говорить на demo

Короткий narrativе удобно строить так:

1. "У нас сохранен existing chat flow, отдельный RAG-чат не создавался."
2. "Retrieval использует уже существующий индекс `fitness_knowledge`."
3. "Контекст собирается через текущий RAG pipeline, включая rewrite и filtering/reranking."
4. "Ответ генерируется локальной моделью через Ollama."
5. "Источники показываются прямо в основном чате, а полные детали доступны в debug sheet."
6. "Для сопоставления local и cloud мы используем один и тот же prepared retrieval context."
7. "Benchmark показывает базовую оценку latency и стабильности без усложнения архитектуры."

## Demo checklist

- MCP/document-index server поднят
- индекс `fitness_knowledge` существует
- Ollama запущена
- в приложении выбран `Local Ollama`
- выбран `RAG Enhanced`
- assistant message показывает `Ollama RAG`
- под ответом виден блок `Источники`
- в details sheet видны grounded sources и quotes
- `Compare local vs cloud` возвращает оба ответа
- `Run benchmark` показывает summary

## Troubleshooting

### MCP retrieval недоступен

Проверьте:

- запущен ли `:mcp-server:runMultiServer`
- доступен ли `Document Index Server` на `8084`
- существует ли индекс `fitness_knowledge`

### Ollama недоступна

Проверьте:

- запущена ли Ollama
- правильный ли host/port указан в приложении
- существует ли модель из настроек

### Local ответ не приходит, а cloud приходит

Обычно это означает:

- модель не скачана в Ollama
- неверное имя модели
- Ollama не принимает соединения с хоста эмулятора

### Источники пустые

Это может быть валидным сценарием, если:

- retrieval нашел недостаточно релевантный контекст
- answerability gate перевел ответ в fallback mode

В этом случае откройте details sheet и проверьте:

- `Grounded Answer`
- `Filtered Out`
- `fallback reason`

## Связанные документы

- [FITNESS_RAG_TESTING_GUIDE.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_RAG_TESTING_GUIDE.md)
- [FITNESS_PRODUCTION_CHAT_FLOW.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_PRODUCTION_CHAT_FLOW.md)
- [FITNESS_RAG_EVALUATION.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_RAG_EVALUATION.md)
