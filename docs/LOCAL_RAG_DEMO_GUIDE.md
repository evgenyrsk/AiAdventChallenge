# Local RAG Demo Guide

Практическая инструкция для демонстрации текущего локального RAG/LLM-сценария в Android приложении.

В этом demo показывается:

- existing chat flow без отдельного RAG-экрана
- локальный retrieval через existing document-index MCP server
- локальная генерация ответа через `LOCAL_OLLAMA`
- configurable optimization profiles для локальной модели
- optional runtime options поверх profile defaults
- compare `Baseline vs Optimized`
- demo-friendly benchmark/evaluation summary

## Что демонстрируем

К концу demo должно быть видно, что приложение умеет:

- отвечать в existing Android chat flow без отдельного local-RAG чата
- использовать existing индекс `fitness_knowledge`
- выполнять retrieval локально через existing document-index runtime
- генерировать ответ через Ollama
- показывать compact sources рядом с ответом ассистента
- показывать retrieval/debug details в bottom sheet
- переключать optimization profile:
  - `BASELINE`
  - `OPTIMIZED_CHAT`
  - `OPTIMIZED_RAG`
- принимать runtime options:
  - `temperature`
  - `num_predict`
  - `num_ctx`
  - `top_k`
  - `top_p`
  - `repeat_penalty`
  - `seed`
  - `stop`
  - `keep_alive`
- сравнивать `Baseline vs Optimized` на одном и том же сценарии
- показывать lightweight quality summary, latency breakdown, tokens/chars
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

Для demo удобно использовать компактную instruct-модель, например:

- `qwen2.5:3b-instruct`
- или другой уже доступный Ollama tag

Важно:

- для Android эмулятора `localhost` и `127.0.0.1` в приложении транслируются в `10.0.2.2`
- поле `Model` в приложении можно использовать и для обычной модели, и для quantized/model variant tag из `ollama list`
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
5. Перейдите к секции `Optimization profile`
6. Для основного RAG demo выберите:
   - `RAG Enhanced`
   - `OPTIMIZED_RAG`
7. Для короткого plain-chat demo можно отдельно выбрать:
   - `Обычный`
   - `OPTIMIZED_CHAT`

Runtime options работают как optional override поверх profile defaults. Для основного demo можно оставить их пустыми, а для усиленного показа вручную задать, например:

- `temperature`
- `num_predict`
- `num_ctx`

Если нужен честный baseline, переключите profile в `BASELINE` без дополнительных override.

## Optimization profiles

В текущем demo доступны три профиля:

- `BASELINE`
  - максимально близок к текущему поведению без дополнительного тюнинга
- `OPTIMIZED_CHAT`
  - короче, стабильнее и удобнее для обычных chat-ответов
- `OPTIMIZED_RAG`
  - строже к groundedness, короче и лучше подходит для retrieval-based answering

Рекомендуемый demo preset:

- основной end-to-end путь:
  - `Local Ollama`
  - `RAG Enhanced`
  - `OPTIMIZED_RAG`
- дополнительный короткий plain-chat путь:
  - `Local Ollama`
  - `Обычный`
  - `OPTIMIZED_CHAT`

Практичный сценарий показа:

1. Один и тот же вопрос показать в `BASELINE`
2. Затем повторить его в `OPTIMIZED_RAG`
3. Если нужно, отдельно показать `OPTIMIZED_CHAT` на plain mode

## Шаг 5. Включить local RAG

На экране чата:

1. Выберите режим `RAG Enhanced`
2. Убедитесь, что выбран backend `Local Ollama`
3. Убедитесь, что selected profile — `OPTIMIZED_RAG`

Именно в этом сценарии демонстрируется:

- retrieval через local document-index runtime
- generation через `LOCAL_OLLAMA`
- prompt/runtime optimization под grounded answering
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
- компактный badge вида `Ollama` или `Ollama RAG`
- latency в миллисекундах
- блок `Источники` под assistant message
- execution line c метаданными:
  - `Profile`
  - `Prompt`
  - `Model`
  - `Retrieval`
  - `Generation`
  - `Tokens`
  - `Chars`

Как это интерпретировать на demo:

- `Profile` = tuning profile локальной модели
- `Prompt` = prompt strategy profile
- `Model` = Ollama tag, включая quantized/model variant при наличии
- `Retrieval` = время retrieval части пайплайна
- `Generation` = время генерации локальной моделью

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

## Шаг 9. Показать compare optimization flow

В том же details sheet запустите compare scenario.

Это основной демонстрационный compare flow для текущей реализации.

Что делает этот сценарий:

- берет тот же `question`
- для RAG использует сопоставимый retrieval сценарий
- отдельно прогоняет ответ через:
  - `BASELINE`
  - `OPTIMIZED_CHAT` или `OPTIMIZED_RAG` в зависимости от answer mode

Что должно появиться в details sheet:

- секция `Baseline vs Optimized`
- статус `ok/error` для обоих прогонов
- latency для baseline и optimized
- baseline answer
- optimized answer
- quality summary
- при наличии:
  - `retrieval`
  - `generation`
  - `tokens`
  - `chars`

Что проговорить:

- сравнивается не другой экран и не другой pipeline, а та же архитектура с разными optimization profiles
- compare показывает practical difference between baseline and optimized
- качество оценивается lightweight heuristic summary, а не сложной research framework

Historical note:

- в проекте раньше был сценарий `local vs cloud`
- сейчас он не является основным демонстрационным compare flow

## Шаг 10. Показать benchmark / evaluation flow

В том же details sheet запустите benchmark:

```text
Run benchmark
```

Что делает benchmark:

- прогоняет несколько фиксированных RAG-вопросов
- для каждого вопроса вызывает compare `baseline vs optimized`
- считает:
  - число samples
  - число кейсов, где оба профиля успешны
  - `Avg baseline latency`
  - `Avg optimized latency`
  - краткие per-sample результаты

Что должно появиться:

- секция `Benchmark Summary`
- `Samples`
- `Both succeeded`
- `Avg baseline latency`
- `Avg optimized latency`
- список коротких результатов по вопросам

Важно проговорить ограничения:

- это demo-friendly benchmark
- это не полноценный low-level profiler CPU/RAM
- resource usage в текущем scope трактуется как:
  - latency
  - tokens
  - chars
  - success/failure
  - profile
  - model
  - `num_ctx`

Это не research framework, а демонстрационный practical benchmark.

## Что говорить на demo

Короткий narrative удобно строить так:

1. "У нас сохранен existing chat flow, отдельный RAG-чат не создавался."
2. "Retrieval использует уже существующий индекс `fitness_knowledge`."
3. "RAG pipeline не переписывался с нуля: он по-прежнему делает rewrite, retrieval и post-processing."
4. "Локальная модель теперь управляется optimization profiles и runtime options."
5. "Optimized prompt/runtime уменьшают hallucination risk и делают ответы стабильнее по длине."
6. "Compare flow показывает difference between baseline and optimized на том же пользовательском сценарии."
7. "Benchmark нужен для practical reproducible demo, а не для научного бенчмаркинга."

## Demo checklist

- MCP/document-index server поднят
- индекс `fitness_knowledge` существует
- Ollama запущена
- в приложении выбран `Local Ollama`
- выбран корректный `Optimization profile`
- при необходимости заданы runtime overrides
- для основного demo выбран `RAG Enhanced`
- assistant message показывает `Ollama` или `Ollama RAG`
- в execution line видны:
  - profile
  - prompt
  - model
  - latency breakdown
- под ответом виден блок `Источники`
- в details sheet видны grounded sources и quotes
- compare section показывает `Baseline vs Optimized`
- benchmark summary показывает baseline/optimized averages

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

### Local ответ не приходит

Обычно это означает:

- модель не скачана в Ollama
- неверное имя модели
- Ollama не принимает соединения с хоста эмулятора

### Compare не показывает разницу

Проверьте:

- выбран ли `BASELINE` для честной отправной точки
- выбран ли `OPTIMIZED_RAG` или `OPTIMIZED_CHAT` для второго сценария
- не заданы ли одинаковые runtime overrides, которые фактически уравнивают два профиля

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
