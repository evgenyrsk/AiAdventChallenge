# Демонстрация File Assistant Tooling

Этот сценарий показывает developer-инструмент для безопасной работы с файлами проекта. Инструмент запускается отдельно от Android-приложения и не встраивается в пользовательский фитнес-чат.

## Что демонстрируем

- Поиск использований компонента по нескольким файлам проекта.
- Анализ найденных мест с группировкой по файлам и строкам.
- Генерацию ADR на основе анализа кода и документации.
- Режим `dry-run`, который показывает diff, но не меняет файлы.
- Режим `--apply`, который создаёт файл и оставляет изменения видимыми через `git diff`.
- Ограничения безопасности для операций чтения и записи.
- Опциональную настройку локального LLM backend через Ollama.

## Подготовка

Запускать команды нужно из корня репозитория:

```bash
pwd
./gradlew :file-assistant-tool:test
```

Ожидаемый результат:
- тесты `:file-assistant-tool:test` проходят успешно;
- Android runtime не требуется для запуска инструмента;
- LLM backend не обязателен для базовой демонстрации.

Дополнительно можно проверить, что Android runtime не затронут:

```bash
./gradlew :app:compileDebugKotlin
```

## Подготовка локальной LLM через Ollama

Этот шаг опциональный. Он нужен, чтобы показать, что file assistant умеет работать в окружении с настраиваемым AI backend. В текущей v1-реализации file operations остаются deterministic: чтение, поиск, запись и diff не зависят от ответа модели.

### Шаг 1. Поднять Ollama

В отдельном терминале:

```bash
ollama serve
```

Если Ollama уже запущена как сервис, команда может сообщить, что порт занят. В этом случае достаточно перейти к проверке моделей.

### Шаг 2. Скачать или проверить модель

Рекомендуемая модель для локального demo:

```bash
ollama pull qwen2.5:3b-instruct
ollama list
```

Ожидаемый результат:
- в списке есть `qwen2.5:3b-instruct`;
- Ollama доступна на `http://localhost:11434`.

Быстрая ручная проверка модели:

```bash
ollama run qwen2.5:3b-instruct "Ответь одним коротким предложением: что такое developer tooling?"
```

### Шаг 3. Настроить file assistant на Ollama backend

В терминале, где будет запускаться file assistant:

```bash
export FILE_ASSISTANT_LLM_BACKEND=ollama
export FILE_ASSISTANT_BASE_URL=http://localhost:11434
export FILE_ASSISTANT_MODEL=qwen2.5:3b-instruct
```

Проверочная команда:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr file-assistant --dry-run"
```

Что показать:
- инструмент видит, что LLM backend настроен;
- в warnings явно указано, что v1 сохраняет deterministic file operations;
- diff preview строится на основе реальных файлов проекта.

### Альтернатива: через private-ai-service

Если нужно показать не прямое подключение к Ollama, а локальный private gateway:

```bash
export PRIVATE_AI_API_KEY=macbook-ollama-private-service-x7k29p
export OLLAMA_BASE_URL=http://localhost:11434
export DEFAULT_MODEL=qwen2.5:3b-instruct
export PORT=8085
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home ./gradlew :private-ai-service:run
```

Во втором терминале:

```bash
curl http://localhost:8085/health
```

Затем настроить file assistant:

```bash
export FILE_ASSISTANT_LLM_BACKEND=private
export FILE_ASSISTANT_BASE_URL=http://localhost:8085
export FILE_ASSISTANT_API_KEY=macbook-ollama-private-service-x7k29p
export FILE_ASSISTANT_MODEL=qwen2.5:3b-instruct
```

Для базовой демонстрации достаточно Ollama напрямую. Private gateway нужен, если хочется показать слой приватного AI backend из проекта.

## Быстрый план демонстрации на 5-7 минут

1. Показать, что инструмент отдельный: `tools/file-assistant`, Gradle task `:file-assistant-tool:run`.
2. Запустить тесты `./gradlew :file-assistant-tool:test`.
3. Опционально поднять локальную LLM: `ollama serve`, `ollama pull`, `ollama list`.
4. Выполнить `find-usages` и показать, что файлы не меняются.
5. Выполнить `generate-adr --dry-run` и показать diff preview.
6. Выполнить `generate-adr --apply` и показать `git diff`.
7. Проговорить ограничения безопасности: project root only, no secrets, dry-run by default.

## Сценарий 1. Найти использования компонента

Команда:

```bash
./gradlew :file-assistant-tool:run --args="find-usages LocalOllamaClient"
```

Что показать в выводе:
- секцию `Goal`;
- секцию `Summary`;
- список `Files Read`;
- строки, где найдено совпадение;
- `Files Changed: None`;
- `Diff: No diff`.

Можно также показать high-level goal на русском языке:

```bash
./gradlew :file-assistant-tool:run --args="--goal 'Найди все места, где используется LocalOllamaClient'"
```

Ожидаемое поведение:
- ассистент сам выбирает сценарий `find-usages`;
- выполняет поиск по разрешённым файлам проекта;
- группирует результаты по файлам;
- не меняет файлы.

Примечание: если в текущем коде символ больше не используется, результат может быть частичным или найденным только в документации/тестах. Это нормальное поведение: инструмент показывает фактическое состояние репозитория, а не выдумывает call sites.

## Сценарий 2. Сгенерировать ADR в dry-run

Команда:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr file-assistant --dry-run"
```

Что показать в выводе:
- `Summary`: dry-run preview подготовлен;
- `Files Read`: какие файлы были использованы как контекст;
- `Files Changed: None`;
- `Diff`: preview нового файла `docs/adr/0001-file-assistant.md`;
- `Warnings`: если LLM не настроен, будет указано, что используется deterministic-анализ; если LLM настроен, будет указано, что file operations всё равно остаются deterministic.

Проверка, что dry-run ничего не записал:

```bash
test ! -f docs/adr/0001-file-assistant.md && echo "dry-run не создал файл"
git diff -- docs/adr
```

Ожидаемый результат:
- файл ADR не создан;
- `git diff -- docs/adr` пустой.

## Сценарий 3. Создать ADR через apply

Перед запуском apply удобно убедиться, что такого ADR ещё нет:

```bash
ls docs/adr 2>/dev/null || echo "docs/adr пока нет"
```

Команда:

```bash
./gradlew :file-assistant-tool:run --args="generate-adr file-assistant --apply"
git diff -- docs/adr
```

Что показать:
- `Files Changed` содержит новый ADR;
- `git diff` показывает добавленный markdown-файл;
- изменения можно ревьюить обычным Git workflow.

После демонстрации можно убрать созданный файл, если он не нужен в коммите:

```bash
git restore -- docs/adr/0001-file-assistant.md 2>/dev/null || rm -f docs/adr/0001-file-assistant.md
```

Если файл был untracked, команда `git restore` его не удалит, поэтому fallback `rm -f` нужен именно для cleanup после demo.

## Сценарий 4. JSON output

Команда:

```bash
./gradlew :file-assistant-tool:run --args="find-usages LocalOllamaClient --json"
```

Что показать:
- результат содержит поля `goal`, `status`, `filesRead`, `filesChanged`, `summary`, `diff`, `warnings`, `nextSteps`;
- формат подходит для дальнейшей автоматизации.

## Безопасность file operations

В демонстрации стоит проговорить ограничения:

- все пути только относительно корня проекта;
- absolute paths запрещены;
- `../` path traversal запрещён;
- symlink наружу из project root запрещён;
- чтение и запись ограничены по размеру;
- запись по умолчанию работает как dry-run;
- реальные изменения требуют `--apply`;
- запись разрешена только в безопасные tooling/docs области;
- секреты и binary/media файлы не читаются и не изменяются.

Запрещённые области:

- `.git/`
- `.gradle/`
- `.idea/`
- `build/`
- `generated/`
- `node_modules/`
- output-директории

Разрешённые расширения:

- `.kt`
- `.kts`
- `.md`
- `.json`
- `.yaml`
- `.yml`
- `.xml`
- `.txt`

## Что важно проговорить про AI-инструменты

В этой задаче AI-инструменты не заменяют Git, Gradle или файловую систему. Они выполняют роль управляющего developer-assistant слоя:

- принимают высокоуровневую цель;
- планируют, какие безопасные file tools вызвать;
- собирают контекст из реальных файлов;
- возвращают структурированный результат;
- показывают diff перед изменениями;
- оставляют финальный контроль разработчику.

Главный тезис для курса: это пример перехода от “AI как чат” к “AI как tool-using development assistant”.

## Критерии успешной демонстрации

Демонстрация считается успешной, если:

- команда `find-usages` возвращает отчёт и не меняет файлы;
- команда `generate-adr --dry-run` возвращает diff preview и не создаёт файл;
- команда `generate-adr --apply` создаёт markdown-файл и изменения видны через `git diff`;
- вывод содержит понятные `Files Read`, `Files Changed`, `Warnings`, `Next Steps`;
- локальная LLM может быть поднята через Ollama, но file operations остаются безопасными и deterministic;
- основной Android runtime компилируется отдельно и не зависит от file assistant.
