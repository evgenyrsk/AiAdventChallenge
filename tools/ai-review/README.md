# AI Code Review Tooling

CI-инструмент для автоматического AI review Pull Request. Исполняемый runner находится в Kotlin/JVM-модуле `:ai-review-tool`, а эта папка содержит prompt, config, README и runtime output.

Инструмент намеренно отделён от Android runtime: он не меняет `ChatScreen`, `ChatViewModel`, production chat flow, локальный RAG приложения и MCP runtime.

## Что Демонстрирует

- GitHub Action запускается на Pull Request.
- Workflow получает PR diff и список изменённых файлов.
- Kotlin runner фильтрует build/generated/binary/media файлы.
- Runner собирает lightweight RAG context из `README.md`, `docs/`, root markdown-документов и соседних исходников.
- Runner вызывает настраиваемый LLM backend.
- Результат пишется в `tools/ai-review/output/review.md`, workflow logs, GitHub Step Summary и artifact.
- PR comment можно включить отдельно через `AI_REVIEW_POST_COMMENT=true`.

## Какой Сценарий Выбрать

| Цель | Сценарий |
| --- | --- |
| Быстро проверить, что Kotlin runner работает | [1. Локальная проверка без LLM backend](#1-локальная-проверка-без-llm-backend) |
| Получить настоящее AI review без GitHub Actions и без VPS | [2. Локальная проверка с Ollama напрямую](#2-локальная-проверка-с-ollama-напрямую) |
| Проверить contract `private-ai-service` локально | [3. Локальная проверка через private-ai-service](#3-локальная-проверка-через-private-ai-service) |
| Проверить GitHub workflow без сервера и ключей | [4. GitHub Actions smoke без LLM backend](#4-github-actions-smoke-без-llm-backend) |
| Получить настоящее AI review в GitHub Actions без VPS | [5. GitHub Actions через self-hosted runner + Ollama](#5-github-actions-через-self-hosted-runner--ollama) |
| Проверить `private-ai-service` в GitHub Actions без VPS | [6. GitHub Actions через self-hosted runner + private-ai-service](#6-github-actions-через-self-hosted-runner--private-ai-service) |
| Production-like CI на GitHub-hosted runner | [7. GitHub Actions через cloud/VPS](#7-github-actions-через-cloudvps) |

Для демонстрации без VPS основной рекомендуемый путь: **self-hosted runner + Ollama**.

## Backend И Переменные

Kotlin runner читает env:

```bash
export AI_REVIEW_BACKEND=private # private | cloud | ollama
export AI_REVIEW_BASE_URL=https://your-private-ai-service.example.com
export AI_REVIEW_API_KEY=your-token
export AI_REVIEW_MODEL=qwen2.5:3b-instruct
export AI_REVIEW_MAX_TOKENS=1600
export AI_REVIEW_TIMEOUT_SECONDS=90
```

Поддерживаемые контракты:

- `private`: существующий `private-ai-service`, `POST /v1/chat`.
- `cloud`: OpenAI-compatible endpoint, `POST /v1/chat/completions`.
- `ollama`: локальный/self-hosted Ollama runner, `POST /api/chat`.

GitHub-hosted runner не видит локальный MacBook. Для него `localhost:11434` означает VM GitHub, а не ваш компьютер. Self-hosted runner работает на вашем MacBook, поэтому внутри job `localhost:11434` и `localhost:8085` указывают на сервисы, запущенные на MacBook.

## GitHub Secrets И Variables

В GitHub откройте:

```text
Repository -> Settings -> Secrets and variables -> Actions
```

Там есть две вкладки:

- `Secrets` - скрытые значения, например API keys.
- `Variables` - обычные repo variables, например backend type, model, runner label.

### Secrets

| Name | Что положить | Когда нужно |
| --- | --- | --- |
| `AI_REVIEW_BASE_URL` | Base URL backend без trailing slash | Для secret/public URL; workflow также принимает Variable с тем же именем для non-secret local URLs |
| `AI_REVIEW_API_KEY` | Token для backend | Для `private` или `cloud`; для `ollama` обычно не нужен |

### Variables

| Name | Значение по умолчанию | Комментарий |
| --- | --- | --- |
| `AI_REVIEW_RUNNER` | `ubuntu-latest` | Для self-hosted demo задайте `self-hosted` |
| `AI_REVIEW_BASE_URL` | пусто | Можно задать здесь для self-hosted Ollama/private local URL, например `http://localhost:11434` |
| `AI_REVIEW_BACKEND` | `private` | `private`, `cloud` или `ollama` |
| `AI_REVIEW_MODEL` | пусто | Укажите модель backend |
| `AI_REVIEW_MAX_TOKENS` | `1600` | Лимит ответа review |
| `AI_REVIEW_TIMEOUT_SECONDS` | `90` | Для локальных моделей часто лучше `120` |
| `AI_REVIEW_POST_COMMENT` | `false` | `true` включает PR comment для PR из того же repo |

## Общие Команды Для Локального Diff

Если хотите проверить текущее PR-like состояние, включая staged и unstaged изменения:

```bash
mkdir -p tools/ai-review/output
git diff HEAD > tools/ai-review/output/diff.patch
git diff --name-only HEAD > tools/ai-review/output/changed_files.txt
```

Если хотите сравнить текущую ветку с `main`:

```bash
mkdir -p tools/ai-review/output
git diff main...HEAD > tools/ai-review/output/diff.patch
git diff --name-only main...HEAD > tools/ai-review/output/changed_files.txt
```

Если нужен только unstaged diff:

```bash
mkdir -p tools/ai-review/output
git diff > tools/ai-review/output/diff.patch
git diff --name-only > tools/ai-review/output/changed_files.txt
```

## 1. Локальная Проверка Без LLM Backend

Цель: проверить, что Kotlin runner компилируется, читает diff/changed files, собирает RAG context и пишет fallback `review.md`.

### Шаги

1. Проверить компиляцию:

```bash
./gradlew :ai-review-tool:compileKotlin
```

2. Подготовить diff:

```bash
mkdir -p tools/ai-review/output
git diff HEAD > tools/ai-review/output/diff.patch
git diff --name-only HEAD > tools/ai-review/output/changed_files.txt
```

3. Запустить runner без `AI_REVIEW_BASE_URL`:

```bash
unset AI_REVIEW_BASE_URL
unset AI_REVIEW_API_KEY

./gradlew :ai-review-tool:run --args="\
  --repo-root . \
  --diff-file tools/ai-review/output/diff.patch \
  --changed-files-file tools/ai-review/output/changed_files.txt \
  --config tools/ai-review/config/review_config.yaml \
  --prompt-template tools/ai-review/prompts/code_review_prompt.md \
  --output tools/ai-review/output/review.md"
```

4. Проверить output:

```bash
sed -n '1,220p' tools/ai-review/output/review.md
```

### Ожидаемо

- В stdout видно `Filtered changed files`.
- В stdout видно `Selected RAG context sources`.
- Создан `tools/ai-review/output/review.md`.
- В review есть fallback `AI_REVIEW_BASE_URL is required`.

Это проверяет plumbing, но не качество LLM review.

## 2. Локальная Проверка С Ollama Напрямую

Цель: получить настоящее AI review без GitHub Actions и без VPS.

### Что Нужно Поднять

- Ollama.
- Локальную модель, например `qwen2.5:3b-instruct`.

### Шаги

1. В отдельном терминале запустить Ollama:

```bash
ollama serve
```

Если Ollama уже запущен как приложение/служба, этот шаг может быть не нужен.

2. Установить или проверить модель:

```bash
ollama pull qwen2.5:3b-instruct
ollama list
```

3. Проверить HTTP API:

```bash
curl http://localhost:11434/api/tags
```

4. Подготовить diff:

```bash
mkdir -p tools/ai-review/output
git diff HEAD > tools/ai-review/output/diff.patch
git diff --name-only HEAD > tools/ai-review/output/changed_files.txt
```

5. Задать env:

```bash
export AI_REVIEW_BACKEND=ollama
export AI_REVIEW_BASE_URL=http://localhost:11434
export AI_REVIEW_MODEL=qwen2.5:3b-instruct
export AI_REVIEW_MAX_TOKENS=1600
export AI_REVIEW_TIMEOUT_SECONDS=120
unset AI_REVIEW_API_KEY
```

6. Запустить runner:

```bash
./gradlew :ai-review-tool:run --args="\
  --repo-root . \
  --diff-file tools/ai-review/output/diff.patch \
  --changed-files-file tools/ai-review/output/changed_files.txt \
  --config tools/ai-review/config/review_config.yaml \
  --prompt-template tools/ai-review/prompts/code_review_prompt.md \
  --output tools/ai-review/output/review.md"
```

### Ожидаемо

- В stdout есть `LLM request completed ... using backend=ollama`.
- `tools/ai-review/output/review.md` начинается с `# AI Code Review`.
- Review содержит секции `Summary`, `Potential Bugs`, `Architecture Concerns`, `Maintainability`, `Recommendations`, `Questions`.

## 3. Локальная Проверка Через private-ai-service

Цель: проверить backend contract `private`, который позже можно вынести на VPS.

### Что Нужно Поднять

- Ollama.
- `private-ai-service` на `http://localhost:8085`.

### Шаги

1. Запустить Ollama:

```bash
ollama serve
ollama pull qwen2.5:3b-instruct
```

2. В отдельном терминале задать env для `private-ai-service`:

```bash
export PRIVATE_AI_API_KEY=local-ai-review-demo-key
export OLLAMA_BASE_URL=http://localhost:11434
export DEFAULT_MODEL=qwen2.5:3b-instruct
export PORT=8085
export REQUEST_TIMEOUT_MS=120000
```

3. Запустить service:

```bash
./gradlew :private-ai-service:run
```

4. Проверить health:

```bash
curl http://localhost:8085/health
```

5. Опционально запустить smoke:

```bash
PRIVATE_AI_API_KEY=local-ai-review-demo-key scripts/private-ai-service-smoke.sh
```

6. В терминале для AI review задать env:

```bash
export AI_REVIEW_BACKEND=private
export AI_REVIEW_BASE_URL=http://localhost:8085
export AI_REVIEW_API_KEY=local-ai-review-demo-key
export AI_REVIEW_MODEL=qwen2.5:3b-instruct
export AI_REVIEW_MAX_TOKENS=1600
export AI_REVIEW_TIMEOUT_SECONDS=120
```

7. Подготовить diff и запустить runner:

```bash
mkdir -p tools/ai-review/output
git diff HEAD > tools/ai-review/output/diff.patch
git diff --name-only HEAD > tools/ai-review/output/changed_files.txt

./gradlew :ai-review-tool:run --args="\
  --repo-root . \
  --diff-file tools/ai-review/output/diff.patch \
  --changed-files-file tools/ai-review/output/changed_files.txt \
  --config tools/ai-review/config/review_config.yaml \
  --prompt-template tools/ai-review/prompts/code_review_prompt.md \
  --output tools/ai-review/output/review.md"
```

### Ожидаемо

- В stdout есть `LLM request completed ... using backend=private`.
- `review.md` содержит настоящее AI review.

## 4. GitHub Actions Smoke Без LLM Backend

Цель: проверить workflow на PR без secrets/server.

### Условия

- `.github/workflows/ai-code-review.yml` уже merged в base branch.
- Открывается отдельный тестовый PR.

### GitHub Config

В `Repository -> Settings -> Secrets and variables -> Actions`:

- не задавать `AI_REVIEW_BASE_URL`;
- не задавать `AI_REVIEW_API_KEY`;
- оставить `AI_REVIEW_POST_COMMENT=false`;
- `AI_REVIEW_RUNNER` можно не задавать, тогда будет `ubuntu-latest`.

### Шаги

1. Открыть тестовый PR с небольшим изменением.
2. Открыть `Actions -> AI Code Review -> Review pull request`.
3. Проверить step `Prepare pull request diff`.
4. Проверить step `Run AI review`.
5. Открыть Step Summary или artifact `ai-code-review`.

### Ожидаемо

- Job запускается.
- Diff/changed files собираются.
- Step Summary/artifact содержит fallback `AI_REVIEW_BASE_URL is required`.

Это не проверяет качество LLM review, только GitHub Actions plumbing.

## 5. GitHub Actions Через Self-Hosted Runner + Ollama

Главный сценарий для полноценной демонстрации без VPS.

### Схема

```text
GitHub PR -> GitHub Actions -> job выполняется на MacBook -> localhost:11434 -> Ollama
```

В этом сценарии `localhost:11434` внутри workflow - это ваш MacBook, потому что self-hosted runner process запущен на MacBook.

### Что Нужно Поднять

- GitHub self-hosted runner process.
- Ollama.
- Локальную модель Ollama.

Не нужно поднимать:

- VPS;
- `private-ai-service`;
- Android app;
- MCP server.

### Шаг 1. Установить Self-Hosted Runner В GitHub

1. Откройте GitHub repository.
2. Перейдите в:

```text
Repository -> Settings -> Actions -> Runners -> New self-hosted runner
```

3. Выберите:

```text
Runner image: macOS
Architecture: arm64 для Apple Silicon или x64 для Intel Mac
```

4. GitHub покажет команды для скачивания и настройки runner. Выполните их на MacBook в отдельной папке, например:

```bash
mkdir actions-runner
cd actions-runner
```

Дальше используйте именно команды из GitHub UI: download package, extract package, затем:

```bash
./config.sh --url <repo-url-from-github> --token <token-from-github>
```

5. Запустите runner:

```bash
./run.sh
```

6. В GitHub на странице `Runners` убедитесь, что runner появился и находится в состоянии `Idle`.

Важно: пока идёт демонстрация, терминал с `./run.sh` должен оставаться открытым.

### Шаг 2. Поднять Ollama На MacBook

В отдельном терминале:

```bash
ollama serve
```

Если Ollama уже запущен приложением или сервисом, команда может сообщить, что порт занят. Тогда переходите дальше.

Установите модель:

```bash
ollama pull qwen2.5:3b-instruct
ollama list
```

Проверьте API:

```bash
curl http://localhost:11434/api/tags
```

### Шаг 3. Настроить GitHub Variables

Откройте:

```text
Repository -> Settings -> Secrets and variables -> Actions -> Variables
```

Создайте:

```text
AI_REVIEW_RUNNER=self-hosted
AI_REVIEW_BACKEND=ollama
AI_REVIEW_BASE_URL=http://localhost:11434
AI_REVIEW_MODEL=qwen2.5:3b-instruct
AI_REVIEW_TIMEOUT_SECONDS=120
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_POST_COMMENT=false
```

`AI_REVIEW_API_KEY` для Ollama не создавайте.

### Шаг 4. Открыть Тестовый PR

1. Убедитесь, что workflow уже merged в base branch.
2. Создайте отдельную ветку.
3. Сделайте небольшое изменение, например в README или docs.
4. Откройте Pull Request из своей ветки в тот же repository.

Не используйте непроверенный fork PR для self-hosted runner demo.

### Шаг 5. Проверить Workflow

В GitHub откройте:

```text
Actions -> AI Code Review -> Review pull request
```

Проверьте:

- job assigned to self-hosted runner;
- step `Prepare pull request diff` показывает changed files;
- step `Run AI review` пишет `LLM request completed ... using backend=ollama`;
- Step Summary содержит настоящее `# AI Code Review`;
- artifact `ai-code-review` содержит `review.md` и `changed_files.txt`.

### Safety Notes

- MacBook должен быть включён.
- Терминал с `./run.sh` должен работать.
- Ollama должен быть запущен до старта job.
- Не запускайте workflow для непроверенных fork PR на личной машине.
- Если модель отвечает медленно, увеличьте `AI_REVIEW_TIMEOUT_SECONDS`.

## 6. GitHub Actions Через Self-Hosted Runner + private-ai-service

Цель: проверить тот же `private` backend, но без VPS.

### Что Нужно Поднять

- GitHub self-hosted runner process.
- Ollama.
- `private-ai-service` на `http://localhost:8085`.

### Шаги

1. Настройте и запустите self-hosted runner как в сценарии 5.

2. Запустите Ollama:

```bash
ollama serve
ollama pull qwen2.5:3b-instruct
```

3. В отдельном терминале запустите `private-ai-service`:

```bash
export PRIVATE_AI_API_KEY=local-ai-review-demo-key
export OLLAMA_BASE_URL=http://localhost:11434
export DEFAULT_MODEL=qwen2.5:3b-instruct
export PORT=8085
export REQUEST_TIMEOUT_MS=120000

./gradlew :private-ai-service:run
```

4. Проверьте service:

```bash
curl http://localhost:8085/health
```

5. В GitHub `Secrets` создайте:

```text
AI_REVIEW_API_KEY=local-ai-review-demo-key
```

6. В GitHub `Variables` создайте:

```text
AI_REVIEW_RUNNER=self-hosted
AI_REVIEW_BACKEND=private
AI_REVIEW_BASE_URL=http://localhost:8085
AI_REVIEW_MODEL=qwen2.5:3b-instruct
AI_REVIEW_TIMEOUT_SECONDS=120
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_POST_COMMENT=false
```

7. Откройте тестовый PR из своей ветки.

### Ожидаемо

- Job выполняется на self-hosted runner.
- Step `Run AI review` пишет `LLM request completed ... using backend=private`.
- Summary/artifact содержит настоящее AI review.

## 7. GitHub Actions Через Cloud/VPS

Цель: production-like CI на GitHub-hosted runner.

### Когда Использовать

- Есть cloud provider с OpenAI-compatible API.
- Или `private-ai-service` развернут на публично доступном VPS/domain.

### GitHub Config Для Cloud

Secrets:

```text
AI_REVIEW_BASE_URL=https://api.openai.com
AI_REVIEW_API_KEY=<provider-api-key>
```

Variables:

```text
AI_REVIEW_RUNNER=ubuntu-latest
AI_REVIEW_BACKEND=cloud
AI_REVIEW_MODEL=<chat-model-name>
AI_REVIEW_TIMEOUT_SECONDS=90
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_POST_COMMENT=false
```

Для другого OpenAI-compatible provider укажите его base URL. Runner сам добавит `/v1/chat/completions`.

### GitHub Config Для VPS private-ai-service

Secrets:

```text
AI_REVIEW_BASE_URL=https://<your-vps-domain>
AI_REVIEW_API_KEY=<PRIVATE_AI_API_KEY from private-ai-service>
```

Variables:

```text
AI_REVIEW_RUNNER=ubuntu-latest
AI_REVIEW_BACKEND=private
AI_REVIEW_MODEL=qwen2.5:3b-instruct
AI_REVIEW_TIMEOUT_SECONDS=90
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_POST_COMMENT=false
```

### Ожидаемо

- Job выполняется на GitHub-hosted runner.
- Step `Run AI review` пишет `LLM request completed ... using backend=cloud` или `private`.
- Summary/artifact содержит настоящее AI review.
- Если `AI_REVIEW_POST_COMMENT=true` и PR из того же repository, PR получает или обновляет один comment с marker `<!-- ai-code-review -->`.

## Проверка Ограничений

Large diff:

- Уменьшите `max_diff_chars` в `tools/ai-review/config/review_config.yaml`.
- Запустите локальный review.
- Ожидаемо: review metadata содержит `diff_truncated=true`, а prompt получает обрезанный diff.

Missing docs:

- Запустите runner в тестовом repo/root без `docs/` или временно уберите docs из `doc_roots` в config.
- Ожидаемо: review явно работает с ограниченным RAG context.

Filtered files:

- Добавьте в `changed_files.txt` путь к `.png`, `build/`, `.gradle/`, `.idea/` или `tools/ai-review/output`.
- Ожидаемо: runner исключит эти файлы из анализа.

## Troubleshooting

- `AI_REVIEW_BASE_URL is required`: backend не настроен; для smoke/demo без LLM это допустимый fallback.
- `LLM request timed out`: увеличьте `AI_REVIEW_TIMEOUT_SECONDS` или проверьте доступность backend с runner.
- GitHub-hosted runner не видит локальный MacBook/Ollama: используйте self-hosted runner, VPS или cloud endpoint.
- Self-hosted job висит в очереди: проверьте, что `./run.sh` запущен и runner в GitHub имеет статус `Idle`.
- `localhost:11434` не отвечает на self-hosted runner: проверьте, что Ollama запущен на MacBook до старта job.
- Secrets пустые на fork PR: GitHub ограничивает передачу secrets в fork-контекстах; используйте summary/artifact или PR из того же repo для полной demo.
- Review не появился comment-ом: проверьте `AI_REVIEW_POST_COMMENT=true`; comment публикуется только для PR из того же репозитория.
- Workflow не запустился на первом PR с этим файлом: сначала нужен merge workflow в base branch, затем откройте отдельный тестовый PR.

## Безопасность

- Workflow использует два checkout:
  - `base-repo`: trusted Kotlin tooling из base commit.
  - `pr-repo`: PR contents только как данные для diff/context.
- Workflow запускает trusted base-branch `:ai-review-tool` Gradle task, а не PR-provided scripts.
- Android app tasks не запускаются.
- API keys читаются только из secrets/env и не печатаются.
- Binary, generated, build, VCS и IDE paths исключаются.
- Существующий Android RAG/MCP runtime не изменяется и не требуется для CI review.
- Для self-hosted runner не запускайте непроверенные fork PR на личной машине.
