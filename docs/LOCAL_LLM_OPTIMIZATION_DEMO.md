# Local LLM Optimization Demo

Готовый и воспроизводимый demo-сценарий для показа оптимизации локальной модели под текущий use case Android приложения.

Документ сфокусирован именно на задаче:

- настроить параметры локальной модели
- попробовать quantized/model variant
- изменить prompt-шаблон под конкретный кейс
- сравнить `BASELINE` vs `OPTIMIZED_*` по качеству, скорости и lightweight resource usage

## Goal

К концу demo должно быть видно, что:

- локальная модель в existing chat flow управляется через `Local Ollama`
- параметры модели можно настраивать прямо в приложении
- для обычного chat и для RAG доступны разные optimization profiles
- prompt optimization влияет на стиль и groundedness ответа
- compare flow показывает `Baseline vs Optimized`
- benchmark даёт воспроизводимое сравнение latency и lightweight resource metrics

## Demo Setup

Перед демонстрацией должны быть доступны:

- Android эмулятор или устройство
- поднятый `mcp-server`
- актуальный индекс `fitness_knowledge`
- Ollama на хост-машине
- локальная модель `qwen2.5:3b-instruct`
- optional: quantized tag той же модели, если он реально есть в `ollama list`

Команды для старта:

```bash
./gradlew :mcp-server:runMultiServer
```

Во втором терминале:

```bash
bash scripts/reindex-fitness-knowledge.sh
```

В третьем терминале:

```bash
./gradlew :app:installDebug
```

Проверка локальной модели:

```bash
ollama list
```

## Concrete Baseline Config

Использовать этот baseline как честную отправную точку:

- `Model = qwen2.5:3b-instruct`
- `Answer mode = RAG Enhanced`
- `Optimization profile = BASELINE`
- runtime overrides = none

Это означает:

- без дополнительного optimization prompt suffix
- без тюнинга runtime parameters поверх текущего базового поведения
- compare показывает, как система ведёт себя “как есть”

## Concrete Optimized RAG Config

Это основной optimized preset для demo:

- `Model = qwen2.5:3b-instruct`
- `Answer mode = RAG Enhanced`
- `Optimization profile = OPTIMIZED_RAG`
- `temperature = 0.1`
- `num_predict = 280`
- `num_ctx = 6144`
- `top_k = 30`
- `top_p = 0.85`
- `repeat_penalty = 1.15`
- `seed = 42`
- `keep_alive = 5m`
- `stop = ["Источники:"]`

Практический смысл:

- меньше randomness
- короче ответ
- больше контроля над groundedness
- меньше вероятность “расползания” ответа за пределы retrieved context

## Concrete Optimized Chat Config

Это optional second scenario для plain chat:

- `Model = qwen2.5:3b-instruct`
- `Answer mode = Обычный`
- `Optimization profile = OPTIMIZED_CHAT`
- `temperature = 0.2`
- `num_predict = 320`
- `num_ctx = 4096`
- `top_k = 40`
- `top_p = 0.9`
- `repeat_penalty = 1.1`

Практический смысл:

- ответ становится более коротким и стабильным
- модель меньше уходит в лишние пояснения
- удобно показать, что optimization зависит от конкретного сценария использования

## Quantized Variant Config

Если в `ollama list` есть quantized/model variant той же модели, используйте его как третий прогон.

Пример:

- `Model = qwen2.5:3b-instruct-q4_K_M`

Если такого тега нет:

- взять ближайший реально доступный `q4` / `q5` / `q8` tag той же модели

Для quantized run использовать тот же optimized preset:

- `Answer mode = RAG Enhanced`
- `Optimization profile = OPTIMIZED_RAG`
- `temperature = 0.1`
- `num_predict = 280`
- `num_ctx = 6144`
- `top_k = 30`
- `top_p = 0.85`
- `repeat_penalty = 1.15`
- `seed = 42`
- `keep_alive = 5m`
- `stop = ["Источники:"]`

Важно:

- quantized run — only-if-available сценарий
- если quantized tag отсутствует, этот шаг пропускается без имитации

## Prompt Changes

### Baseline prompt narrative

Baseline использует текущее системное поведение без дополнительного optimization suffix.

### Optimized RAG prompt narrative

Для `OPTIMIZED_RAG` объясняем на demo так:

- отвечай кратко и по существу
- если данных недостаточно, прямо скажи об этом
- не добавляй факты вне retrieved context
- сначала вывод, затем 2-4 коротких уточнения
- не делай категоричных утверждений без подтверждения из контекста

### Optimized Chat prompt narrative

Для `OPTIMIZED_CHAT` объясняем так:

- отвечай кратко, по делу и без лишней воды
- не выдумывай факты
- если уверенности мало, прямо обозначь это
- сначала дай самый полезный вывод, затем 2-4 практичных детали

## Demo Questions

Основной канонический вопрос:

```text
Почему сон влияет на восстановление и контроль аппетита?
```

Дополнительные вопросы:

- `Что важнее для похудения: дефицит калорий или время приема пищи?`
- `Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?`

Optional fallback check:

- `Как принимать креатин моногидрат: нужна ли фаза загрузки и сколько граммов в день?`

Этот вопрос полезен для демонстрации честного ограничения, если релевантного контекста недостаточно.

## Demo Steps

### Шаг 1. Поднять retrieval runtime

Запустить:

```bash
./gradlew :mcp-server:runMultiServer
```

### Шаг 2. Убедиться, что индекс готов

Запустить:

```bash
bash scripts/reindex-fitness-knowledge.sh
```

### Шаг 3. Проверить доступные модели Ollama

```bash
ollama list
```

Проверить:

- есть `qwen2.5:3b-instruct`
- optional: есть quantized/model variant той же модели

### Шаг 4. Запустить Android app

```bash
./gradlew :app:installDebug
```

### Шаг 5. Выбрать `Local Ollama`

В `Strategy Settings`:

- `AI Backend = Local Ollama`
- `Host = 10.0.2.2` или актуальный host
- `Port = 11434`
- `Model = qwen2.5:3b-instruct`

### Шаг 6. Установить baseline config

В приложении:

- `Answer mode = RAG Enhanced`
- `Optimization profile = BASELINE`
- runtime overrides = пусто

### Шаг 7. Задать канонический вопрос

```text
Почему сон влияет на восстановление и контроль аппетита?
```

Зафиксировать:

- answer
- quality summary
- total latency
- retrieval
- generation
- total tokens
- response chars

### Шаг 8. Переключиться на optimized RAG

В `Strategy Settings`:

- `Optimization profile = OPTIMIZED_RAG`
- при необходимости вручную задать:
  - `temperature = 0.1`
  - `num_predict = 280`
  - `num_ctx = 6144`
  - `top_k = 30`
  - `top_p = 0.85`
  - `repeat_penalty = 1.15`
  - `seed = 42`
  - `keep_alive = 5m`
  - `stop = Источники:`

### Шаг 9. Повторить тот же вопрос

Снова отправить:

```text
Почему сон влияет на восстановление и контроль аппетита?
```

Показать compare section:

- `Baseline vs Optimized`
- baseline answer
- optimized answer
- quality summary
- latency

### Шаг 10. Optional: показать optimized chat

Если нужен второй сценарий:

- `Answer mode = Обычный`
- `Optimization profile = OPTIMIZED_CHAT`

Задать один из тех же вопросов и показать:

- более короткий ответ
- меньше лишних деталей
- более стабильную структуру ответа

### Шаг 11. Optional: повторить optimized run на quantized model

Если quantized tag реально есть:

- заменить только `Model`
- оставить `OPTIMIZED_RAG`
- повторить тот же вопрос

Показать:

- что latency уменьшилась или стала стабильнее
- что качество осталось приемлемым для demo

### Шаг 12. Запустить benchmark

В details sheet запустить benchmark и показать:

- `Samples`
- `Both succeeded`
- `Avg baseline latency`
- `Avg optimized latency`
- короткие per-sample результаты

## Comparison Table Template

### Table 1. Config Comparison

| Scenario | Model | Profile | Temperature | Num predict | Num ctx | Top K | Top P | Repeat penalty |
|---|---|---|---:|---:|---:|---:|---:|---:|
| Baseline | qwen2.5:3b-instruct | BASELINE | default | default | default | default | default | default |
| Optimized RAG | qwen2.5:3b-instruct | OPTIMIZED_RAG | 0.1 | 280 | 6144 | 30 | 0.85 | 1.15 |
| Quantized optimized | qwen2.5:3b-instruct-q4_K_M | OPTIMIZED_RAG | 0.1 | 280 | 6144 | 30 | 0.85 | 1.15 |

### Table 2. Result Comparison

| Scenario | Quality summary | Hallucination risk | Total latency | Retrieval latency | Generation latency | Total tokens | Response chars |
|---|---|---|---:|---:|---:|---:|---:|
| Baseline |  |  |  |  |  |  |  |
| Optimized RAG |  |  |  |  |  |  |  |
| Quantized optimized |  |  |  |  |  |  |  |

### Table 3. Final Takeaway

| Variant | Main conclusion |
|---|---|
| Baseline |  |
| Optimized |  |
| Quantized optimized |  |

## Expected Observations

### Quality before optimization

Ожидание для `BASELINE`:

- ответ полезный, но обычно длиннее
- выше шанс лишних пояснений
- weaker control over groundedness

### Quality after optimization

Ожидание для `OPTIMIZED_RAG`:

- ответ короче
- ответ чище опирается на retrieved context
- модель чаще честно говорит о нехватке данных
- ниже hallucination risk

### Speed

Смотреть:

- `Total latency`
- `Retrieval latency`
- `Generation latency`

Ожидание:

- `OPTIMIZED_RAG` должен быть не хуже baseline по practical responsiveness
- quantized/model variant, если есть, должен быть быстрее или стабильнее по `generation`

### Lightweight resource usage

Смотреть:

- `tokens`
- `chars`
- `model`
- `profile`
- `num_ctx`

Ожидание:

- `OPTIMIZED_RAG` обычно даёт меньше `completion tokens`
- `OPTIMIZED_RAG` обычно даёт меньше `response chars`
- quality остаётся лучше или как минимум не хуже baseline

## What To Say During Demo

Удобный narrative:

1. "Сначала показываем baseline без дополнительного тюнинга."
2. "Потом включаем optimized profile с конкретными runtime параметрами."
3. "Дальше меняем prompt behavior под RAG-сценарий: короче, строже и без выдумывания фактов вне контекста."
4. "После этого сравниваем качество, latency и lightweight resource metrics."
5. "Если доступен quantized/model variant, проверяем можно ли сохранить качество и выиграть по скорости."

## Notes And Limitations

- quantization показывается только если quantized tag реально есть в `ollama list`
- benchmark здесь pragmatic, а не research-grade
- resource usage в этом demo оценивается через:
  - latency
  - tokens
  - chars
  - success/failure
  - model/profile/num_ctx
- low-level CPU/RAM profiler в demo не показывается

## Related Guides

- [docs/LOCAL_RAG_DEMO_GUIDE.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/LOCAL_RAG_DEMO_GUIDE.md)
- [docs/FITNESS_RAG_TESTING_GUIDE.md](/Users/evgenyrsk/AndroidStudioProjects/AiAdventChallenge/docs/FITNESS_RAG_TESTING_GUIDE.md)
