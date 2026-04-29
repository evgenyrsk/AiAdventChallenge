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

## Backend

Переменные окружения:

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

Для GitHub-hosted runner локальный Ollama на MacBook недоступен, если он не опубликован наружу. Для CI используйте private AI service на доступном сервере, cloud-compatible endpoint или self-hosted runner.

## Откуда Брать Secrets И Variables

В GitHub откройте:

```text
Repository -> Settings -> Secrets and variables -> Actions
```

Там есть две вкладки:

- `Secrets` - значения скрыты в логах, сюда кладём URL backend и API key.
- `Variables` - обычные repo variables, сюда кладём backend type, model и feature flags.

### Secrets

| Name | Что положить | Откуда взять |
| --- | --- | --- |
| `AI_REVIEW_BASE_URL` | Base URL LLM backend без trailing slash | URL private service/VPS, cloud endpoint или Ollama endpoint self-hosted runner |
| `AI_REVIEW_API_KEY` | Token для backend | `PRIVATE_AI_API_KEY` для `private-ai-service` или API key cloud provider |

Если backend не требует ключ, например локальный Ollama на self-hosted runner, `AI_REVIEW_API_KEY` можно не создавать.

### Variables

| Name | Рекомендуемое значение | Комментарий |
| --- | --- | --- |
| `AI_REVIEW_BACKEND` | `private` | `private`, `cloud` или `ollama` |
| `AI_REVIEW_MODEL` | `qwen2.5:3b-instruct` | Для cloud укажите модель провайдера |
| `AI_REVIEW_MAX_TOKENS` | `1600` | Можно не задавать, workflow использует default |
| `AI_REVIEW_TIMEOUT_SECONDS` | `90` | Можно увеличить для медленного backend |
| `AI_REVIEW_POST_COMMENT` | `false` | `true` включает PR comment для PR из того же repo |

### Пример: private-ai-service

Используйте этот вариант, если `private-ai-service` развернут на VPS, сервере или другом URL, доступном из GitHub-hosted runner.

Secrets:

```text
AI_REVIEW_BASE_URL=https://<your-vps-domain>
AI_REVIEW_API_KEY=<PRIVATE_AI_API_KEY from private-ai-service>
```

Variables:

```text
AI_REVIEW_BACKEND=private
AI_REVIEW_MODEL=qwen2.5:3b-instruct
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_TIMEOUT_SECONDS=90
AI_REVIEW_POST_COMMENT=false
```

`AI_REVIEW_API_KEY` должен совпадать с переменной `PRIVATE_AI_API_KEY`, с которой запущен `private-ai-service`. Пример локальной настройки сервиса описан в `docs/PRIVATE_AI_SERVICE.md`, но для GitHub Actions нужен публично достижимый URL, например через VPS/TLS reverse proxy.

### Пример: cloud OpenAI-compatible backend

Используйте этот вариант, если есть cloud provider с OpenAI-compatible Chat Completions API.

Secrets:

```text
AI_REVIEW_BASE_URL=https://api.openai.com
AI_REVIEW_API_KEY=<provider-api-key>
```

Variables:

```text
AI_REVIEW_BACKEND=cloud
AI_REVIEW_MODEL=<chat-model-name>
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_TIMEOUT_SECONDS=90
AI_REVIEW_POST_COMMENT=false
```

Для другого OpenAI-compatible provider укажите его base URL. Runner сам добавит путь `/v1/chat/completions`.

### Пример: Ollama

Используйте только с self-hosted runner, где Ollama доступен из workflow process.

Secrets:

```text
AI_REVIEW_BASE_URL=http://localhost:11434
```

`AI_REVIEW_API_KEY` обычно не нужен.

Variables:

```text
AI_REVIEW_BACKEND=ollama
AI_REVIEW_MODEL=<installed-ollama-model>
AI_REVIEW_MAX_TOKENS=1600
AI_REVIEW_TIMEOUT_SECONDS=120
AI_REVIEW_POST_COMMENT=false
```

На GitHub-hosted runner `localhost:11434` указывает на runner VM, а не на ваш MacBook.

## Локальная Демонстрация

### 1. Проверить компиляцию runner

```bash
./gradlew :ai-review-tool:compileKotlin
```

Ожидаемо: компилируется только tooling-модуль `:ai-review-tool`, Android app tasks не запускаются.

### 2. Подготовить diff

Для изменений в текущей ветке относительно `main`:

```bash
mkdir -p tools/ai-review/output
git diff main...HEAD > tools/ai-review/output/diff.patch
git diff --name-only main...HEAD > tools/ai-review/output/changed_files.txt
```

Для незакоммиченных локальных изменений:

```bash
mkdir -p tools/ai-review/output
git diff > tools/ai-review/output/diff.patch
git diff --name-only > tools/ai-review/output/changed_files.txt
```

Если diff пустой, создайте небольшой тестовый change в отдельной ветке или используйте текущий feature branch.

### 3. Запустить review runner

```bash
./gradlew :ai-review-tool:run --args="\
  --repo-root . \
  --diff-file tools/ai-review/output/diff.patch \
  --changed-files-file tools/ai-review/output/changed_files.txt \
  --config tools/ai-review/config/review_config.yaml \
  --prompt-template tools/ai-review/prompts/code_review_prompt.md \
  --output tools/ai-review/output/review.md"
```

Ожидаемо:

- В stdout видно количество отфильтрованных changed files.
- В stdout видно выбранные RAG context sources.
- Файл `tools/ai-review/output/review.md` создан.
- Если `AI_REVIEW_BASE_URL` не задан, runner не падает, а пишет fallback review с пояснением `AI_REVIEW_BASE_URL is required`.
- Если backend настроен, `review.md` содержит полноценное ревью в формате `# AI Code Review`.

### 4. Проверить результат

```bash
sed -n '1,220p' tools/ai-review/output/review.md
```

Минимально успешная демонстрация без backend: runner собрал diff/context и создал fallback review. Полная демонстрация: backend вернул sections `Summary`, `Potential Bugs`, `Architecture Concerns`, `Maintainability`, `Recommendations`, `Questions`.

## Демонстрация В GitHub Actions

### Вариант A. Минимальная Demo Без LLM Backend

Этот вариант проверяет CI plumbing: workflow запускается, diff собирается, changed files читаются, RAG context собирается, output создаётся. Это не полноценное AI review, потому что backend не настроен.

1. Не задавайте `AI_REVIEW_BASE_URL` и `AI_REVIEW_API_KEY`.
2. Убедитесь, что `.github/workflows/ai-code-review.yml` уже находится в base branch.
3. Откройте тестовый Pull Request с небольшим изменением.
4. Откройте `Actions -> AI Code Review -> Review pull request`.
5. Проверьте step `Run AI review`.

Ожидаемо:

- Workflow не падает.
- В логах видно `Filtered changed files` и `Selected RAG context sources`.
- В Step Summary и artifact `ai-code-review/review.md` есть fallback review.
- В fallback review есть `Backend error: AI_REVIEW_BASE_URL is required`.

Это достаточная демонстрация того, что GitHub Action и Kotlin tooling связаны правильно.

### Вариант B. Полная Demo С AI Review

Этот вариант показывает настоящий вызов LLM backend и структурированное review.

1. Выберите backend: `private`, `cloud` или `ollama` на self-hosted runner.
2. Проверьте доступность backend с машины, эквивалентной runner:
   - для `private`: `GET https://<your-vps-domain>/health`, если сервис поддерживает health endpoint;
   - для `cloud`: проверьте валидность provider API key;
   - для `ollama`: убедитесь, что self-hosted runner видит `http://localhost:11434`.
3. Создайте GitHub Secrets и Variables по таблицам выше.
4. Откройте новый тестовый Pull Request после того, как workflow уже попал в base branch.
5. Откройте `Actions -> AI Code Review -> Review pull request`.
6. Проверьте logs, Step Summary и artifact `ai-code-review`.
7. Если нужен PR comment, установите `AI_REVIEW_POST_COMMENT=true` и используйте PR из того же repo.

Ожидаемо:

- Step `Run AI review` печатает `LLM request completed ... using backend=<backend>`.
- `review.md` начинается с `# AI Code Review`.
- В review есть sections `Summary`, `Potential Bugs`, `Architecture Concerns`, `Maintainability`, `Recommendations`, `Questions`.
- Если включён comment, в PR появляется или обновляется один комментарий с AI review.

### Настроить Repository Secrets/Variables

Secrets:

- `AI_REVIEW_BASE_URL`
- `AI_REVIEW_API_KEY`

Variables:

- `AI_REVIEW_BACKEND`, default `private`
- `AI_REVIEW_MODEL`
- `AI_REVIEW_MAX_TOKENS`, default `1600`
- `AI_REVIEW_TIMEOUT_SECONDS`, default `90`
- `AI_REVIEW_POST_COMMENT`, default `false`

### Открыть Тестовый Pull Request

Важно: workflow `pull_request` исполняется из base branch. Если этот tooling добавляется первым PR, workflow может начать запускаться только после попадания `.github/workflows/ai-code-review.yml` в base branch. Для демонстрации CI откройте следующий тестовый PR уже после merge tooling-PR.

### Проверить Workflow

В GitHub Actions ожидаемо:

- Job `AI Code Review / Review pull request` стартует на PR.
- Step `Prepare pull request diff` печатает первые changed files.
- Step `Run AI review` запускает `./gradlew :ai-review-tool:run`.
- Review виден в workflow logs и GitHub Step Summary.
- Artifact `ai-code-review` содержит `review.md` и `changed_files.txt`.

Если `AI_REVIEW_POST_COMMENT=true` и PR из того же репозитория, workflow создаёт или обновляет один PR comment с marker `<!-- ai-code-review -->`.

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

## Безопасность

- Workflow использует два checkout:
  - `base-repo`: trusted Kotlin tooling из base commit.
  - `pr-repo`: PR contents только как данные для diff/context.
- Workflow запускает trusted base-branch `:ai-review-tool` Gradle task, а не PR-provided scripts.
- Android app tasks не запускаются.
- API keys читаются только из secrets/env и не печатаются.
- Binary, generated, build, VCS и IDE paths исключаются.
- Существующий Android RAG/MCP runtime не изменяется и не требуется для CI review.

## Troubleshooting

- `AI_REVIEW_BASE_URL is required`: backend не настроен; для smoke/demo без LLM это допустимый fallback.
- `LLM request timed out`: увеличьте `AI_REVIEW_TIMEOUT_SECONDS` или проверьте доступность backend с runner.
- GitHub-hosted runner не видит локальный MacBook/Ollama: используйте VPS/public endpoint или self-hosted runner.
- Secrets пустые на fork PR: GitHub ограничивает передачу secrets в fork-контекстах; используйте summary/artifact или PR из того же repo для полной demo.
- Review не появился comment-ом: проверьте `AI_REVIEW_POST_COMMENT=true`; comment публикуется только для PR из того же репозитория.
- Workflow не запустился на первом PR с этим файлом: сначала нужен merge workflow в base branch, затем откройте отдельный тестовый PR.
