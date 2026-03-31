package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.MemoryClassificationRequest
import com.example.aiadventchallenge.domain.model.MemoryClassificationResponse
import com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics
import com.example.aiadventchallenge.domain.model.MemoryEntryCompact
import com.example.aiadventchallenge.domain.model.SingleClassificationItem
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.utils.JsonUtils

// Вспомогательный класс для ответа LLM с токенами
data class LlmResponse(
    val content: String,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
)

data class AiClassifierConfig(
    val temperature: Float = 0.2f,
    val maxTokens: Int = 200,
    val maxTokensConversation: Int = 800
)

data class ConversationPairMultiClassification(
    val userResults: List<ClassificationResult>,
    val assistantResults: List<ClassificationResult>,
    val metrics: MemoryClassificationMetrics
)

data class ConversationPairRequest(
    val userMessage: String,
    val assistantMessage: String,
    val existingWorkingMemory: List<MemoryEntryCompact>,
    val existingLongTermMemory: List<MemoryEntryCompact>
)

@kotlinx.serialization.Serializable
data class ConversationPairResponse(
    val user: List<SingleClassificationItem>? = null,
    val assistant: List<SingleClassificationItem>? = null
)

data class ConversationPairResult(
    val userResults: List<ClassificationResult>,
    val assistantResults: List<ClassificationResult>
)

class AiMemoryClassifier(
    private val repository: AiRepository,
    private val config: AiClassifierConfig = AiClassifierConfig()
) {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun classifyUserMessage(
        message: ChatMessage,
        existingWorkingMemory: List<MemoryEntry>,
        existingLongTermMemory: List<MemoryEntry>
    ): Result<com.example.aiadventchallenge.domain.model.MultipleClassificationResult> {
        return try {
            val startTime = System.currentTimeMillis()

            val request = buildRequest(message, existingWorkingMemory, existingLongTermMemory)
            val response = callLlm(request)
            val executionTime = System.currentTimeMillis() - startTime

            val classificationResults = parseResponse(response)

            val metrics = MemoryClassificationMetrics(
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                executionTimeMs = executionTime
            )

            logClassification(classificationResults, metrics)

            Result.success(
                com.example.aiadventchallenge.domain.model.MultipleClassificationResult(
                    results = classificationResults,
                    metrics = metrics
                )
            )

        } catch (e: Exception) {
            println("❌ AiMemoryClassifier: Error classifying message - ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun classifyAssistantMessage(
        message: ChatMessage,
        workingMemory: List<MemoryEntry>
    ): Result<com.example.aiadventchallenge.domain.model.MultipleClassificationResult> {
        return try {
            val startTime = System.currentTimeMillis()

            val request = buildAssistantRequest(message, workingMemory)
            val response = callLlm(request)
            val executionTime = System.currentTimeMillis() - startTime

            val results = parseAssistantResponse(response)

            val metrics = MemoryClassificationMetrics(
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                executionTimeMs = executionTime
            )

            logAssistantClassification(results, metrics)

            Result.success(
                com.example.aiadventchallenge.domain.model.MultipleClassificationResult(
                    results = results,
                    metrics = metrics
                )
            )

        } catch (e: Exception) {
            println("❌ AiMemoryClassifier: Error classifying assistant message - ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun classifyConversationPair(
        userMessage: ChatMessage,
        assistantMessage: ChatMessage,
        existingWorkingMemory: List<MemoryEntry>,
        existingLongTermMemory: List<MemoryEntry>
    ): Result<ConversationPairMultiClassification> {
        return try {
            val startTime = System.currentTimeMillis()

            val request = buildConversationRequest(userMessage, assistantMessage, existingWorkingMemory, existingLongTermMemory)
            val response = callConversationLlm(request)
            val executionTime = System.currentTimeMillis() - startTime

            val pairResult = parseConversationResponse(response)

            val metrics = MemoryClassificationMetrics(
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                executionTimeMs = executionTime
            )

            logConversationClassification(pairResult, metrics)

            Result.success(
                ConversationPairMultiClassification(
                    userResults = pairResult.userResults,
                    assistantResults = pairResult.assistantResults,
                    metrics = metrics
                )
            )

        } catch (e: Exception) {
            println("❌ AiMemoryClassifier: Error classifying conversation pair - ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun callLlm(request: MemoryClassificationRequest): LlmResponse {
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildUserPrompt(request)

        val config = RequestConfig(
            systemPrompt = systemPrompt,
            temperature = config.temperature.toDouble(),
            maxTokens = config.maxTokens
        )

        return when (val result = repository.askWithUsage(userPrompt, null, config, RequestType.MEMORY_CLASSIFICATION)) {
            is ChatResult.Success -> {
                LlmResponse(
                    content = result.data.content,
                    promptTokens = result.data.promptTokens,
                    completionTokens = result.data.completionTokens,
                    totalTokens = result.data.totalTokens
                )
            }
            is ChatResult.Error -> {
                throw Exception("LLM error: ${result.message}")
            }
        }
    }

    private suspend fun callConversationLlm(request: ConversationPairRequest): LlmResponse {
        val systemPrompt = buildConversationSystemPrompt()
        val userPrompt = buildConversationPrompt(request)

        val config = RequestConfig(
            systemPrompt = systemPrompt,
            temperature = config.temperature.toDouble(),
            maxTokens = config.maxTokensConversation
        )

        return when (val result = repository.askWithUsage(userPrompt, null, config, RequestType.MEMORY_CLASSIFICATION)) {
            is ChatResult.Success -> {
                LlmResponse(
                    content = result.data.content,
                    promptTokens = result.data.promptTokens,
                    completionTokens = result.data.completionTokens,
                    totalTokens = result.data.totalTokens
                )
            }
            is ChatResult.Error -> {
                throw Exception("LLM error: ${result.message}")
            }
        }
    }

    private fun buildSystemPrompt(): String {
        return """Вы эксперт по классификации сообщений в многослойную память ассистента.

ВАЖНЫЕ ПРАВИЛА:
1. Верните ТОЛЬКО валидный JSON. Никакого дополнительного текста.
2. Классифицируйте важные фрагменты сообщения (их может быть несколько) в один из слоев памяти:
   - WORKING: данные текущей задачи (цели, параметры, ограничения)
   - LONG_TERM: профиль пользователя, устойчивые предпочтения, подтвержденные факты
3. Определите reason из списка: TASK_GOAL, TASK_PARAMETER, ACTIVE_ENTITY, INTERMEDIATE_OUTPUT, USER_NAME, USER_PREFERENCE, CONFIRMED_FACT, USER_PROFILE_DATA
4. Определите importance (0.0 - 1.0) - насколько важна информация для будущих диалогов
5. action может быть:
   - "create": создать новую запись
   - "skip": не сохранять (для коротких сообщений, филлеров, приветствий)
6. value - извлеченное значение (краткое, 1-3 слова максимум)
7. key - предложенный ключ для хранения (user_name, task_goal и т.д.)
8. Вы можете вернуть НОЛЬ ИЛИ БОЛЕЕ классификаций для одного сообщения.
9. Если сообщений для сохранения нет, верните пустой массив: {"classifications": []}
10. Каждая классификация в массиве обрабатывается независимо

ПРИМЕРЫ JSON-ОТВЕТА:

1. Одна классификация:
{
  "classifications": [
    {
      "action": "create",
      "memoryType": "working",
      "reason": "TASK_GOAL",
      "importance": 0.9,
      "value": "составить план обучения",
      "key": "task_goal"
    }
  ]
}

2. Несколько классификаций:
{
  "classifications": [
    {
      "action": "create",
      "memoryType": "working",
      "reason": "TASK_GOAL",
      "importance": 0.9,
      "value": "создать приложение",
      "key": "task_goal"
    },
    {
      "action": "create",
      "memoryType": "working",
      "reason": "TASK_PARAMETER",
      "importance": 0.7,
      "value": "Android",
      "key": "platform"
    }
  ]
}

3. Имя пользователя:
{
  "classifications": [
    {
      "action": "create",
      "memoryType": "long_term",
      "reason": "USER_NAME",
      "importance": 0.9,
      "value": "Иван",
      "key": "user_name"
    }
  ]
}

4. Пустой результат:
{
  "classifications": []
}

СТРОГИЙ ФОРМАТ JSON:
{
  "classifications": [
    {
      "action": "create" | "skip",
      "memoryType": "working" | "long_term" | null,
      "reason": "TASK_GOAL" | ... | null,
      "importance": 0.0 - 1.0 | null,
      "value": "извлеченное значение" | null,
      "key": "предложенный ключ" | null
    }
  ]
}"""
    }

    private fun buildConversationSystemPrompt(): String {
        return """Вы эксперт по классификации диалога пользователя и ассистента в многослойную память.

КРИТИЧЕСКИ ВАЖНЫЕ ПРАВИЛА:
1. Верните ТОЛЬКО валидный JSON - НИКАКОГО markdown код-блоков, комментариев или дополнительного текста
2. Не используйте символы ``` или какой-либо форматирование - только чистый JSON
3. Начинайте ответ с символа { и заканчивайте символом }
4. Проанализируйте ОБА сообщения вместе - запрос пользователя и ответ ассистента
5. Классифицируйте важные фрагменты (их может быть несколько) анализируемых сообщений в слои памяти:
    - WORKING: данные текущей задачи (цели, параметры, ограничения)
    - LONG_TERM: профиль пользователя, устойчивые предпочтения, подтвержденные факты
6. Определите reason из списка: TASK_GOAL, TASK_PARAMETER, ACTIVE_ENTITY, INTERMEDIATE_OUTPUT, USER_NAME, USER_PREFERENCE, CONFIRMED_FACT, USER_PROFILE_DATA
7. Определите importance (0.0 - 1.0) - насколько важна информация для будущих диалогов
8. action может быть:
    - "create": создать новую запись
    - "skip": не сохранять (для коротких сообщений, филлеров, приветствий)
9. При анализе ответа ассистента ищите подтвержденные факты, задачи и сущности
10. value - извлеченное значение (краткое, 1-3 слова максимум)
11. key - предложенный ключ для хранения (user_name, task_goal и т.д.)
12. Вы можете вернуть НОЛЬ ИЛИ БОЛЕЕ классификаций для каждого сообщения
13. Если сообщений для сохранения нет, верните пустые массивы
14. Поле "assistant" может быть пустым массивом [] или содержать записи
15. Каждая классификация в массиве обрабатывается независимо

ПРИМЕРЫ JSON-ОТВЕТА:

1. Оба сообщения с классификациями:
{
  "user": [
    {
      "action": "create",
      "memoryType": "working",
      "reason": "TASK_GOAL",
      "importance": 0.9,
      "value": "изучить Kotlin",
      "key": "task_goal"
    }
  ],
  "assistant": [
    {
      "action": "create",
      "memoryType": "working",
      "reason": "ACTIVE_ENTITY",
      "importance": 0.8,
      "value": "Kotlin",
      "key": "language"
    }
  ]
}

2. Только пользовательское сообщение:
{
  "user": [
    {
      "action": "create",
      "memoryType": "long_term",
      "reason": "USER_PROFILE_DATA",
      "importance": 0.8,
      "value": "сидячий образ жизни",
      "key": "lifestyle"
    }
  ],
  "assistant": []
}

3. Пустой результат:
{
  "user": [],
  "assistant": []
}

СТРОГИЙ ФОРМАТ JSON:
{
  "user": [
    {
      "action": "create" | "skip",
      "memoryType": "working" | "long_term" | null,
      "reason": "TASK_GOAL" | ... | null,
      "importance": 0.0 - 1.0 | null,
      "value": "извлеченное значение" | null,
      "key": "предложенный ключ" | null
    }
  ],
  "assistant": [
    {
      "action": "create" | "skip",
      "memoryType": "working" | "long_term" | null,
      "reason": "TASK_GOAL" | ... | null,
      "importance": 0.0 - 1.0 | null,
      "value": "извлеченное значение" | null,
      "key": "предложенный ключ" | null
    }
  ]
}"""
    }

    private fun buildUserPrompt(request: MemoryClassificationRequest): String {
        val workingMemoryText = if (request.existingWorkingMemory.isNotEmpty()) {
            request.existingWorkingMemory.joinToString("\n") { "- ${it.key}: ${it.value} (${it.reason})" }
        } else {
            "(пусто)"
        }

        val longTermMemoryText = if (request.existingLongTermMemory.isNotEmpty()) {
            request.existingLongTermMemory.joinToString("\n") { "- ${it.key}: ${it.value} (${it.reason})" }
        } else {
            "(пусто)"
        }

        return """Классифицируйте следующее сообщение пользователя в многослойную память.

Существующая WORKING память:
$workingMemoryText

Существующая LONG-TERM память:
$longTermMemoryText

Сообщение пользователя:
${request.userMessage}

Верните классификацию в формате JSON."""
    }

    private fun buildConversationPrompt(request: ConversationPairRequest): String {
        val workingMemoryText = if (request.existingWorkingMemory.isNotEmpty()) {
            request.existingWorkingMemory.joinToString("\n") { "- ${it.key}: ${it.value} (${it.reason})" }
        } else {
            "(пусто)"
        }

        val longTermMemoryText = if (request.existingLongTermMemory.isNotEmpty()) {
            request.existingLongTermMemory.joinToString("\n") { "- ${it.key}: ${it.value} (${it.reason})" }
        } else {
            "(пусто)"
        }

        return """Классифицируйте диалог пользователя и ассистента в многослойную память.

Существующая WORKING память:
$workingMemoryText

Существующая LONG-TERM память:
$longTermMemoryText

Диалог:
Пользователь: ${request.userMessage}

Ассистент: ${request.assistantMessage}

Верните классификацию для обоих сообщений в формате JSON."""
    }

    private fun parseResponse(response: LlmResponse): List<ClassificationResult> {
        val cleanedContent = JsonUtils.extractJson(response.content)

        val jsonResponse = try {
            json.decodeFromString<MemoryClassificationResponse>(cleanedContent)
        } catch (e: Exception) {
            println("⚠️ AiMemoryClassifier: Failed to parse JSON (content: $cleanedContent), returning empty list")
            return emptyList()
        }

        val classifications = jsonResponse.classifications ?: return emptyList()

        return classifications.mapNotNull { item ->
            parseSingleClassificationItem(item, MemorySource.USER_EXTRACTED)
        }
    }

    private fun parseAssistantResponse(response: LlmResponse): List<ClassificationResult> {
        val cleanedContent = JsonUtils.extractJson(response.content)

        val jsonResponse = try {
            json.decodeFromString<MemoryClassificationResponse>(cleanedContent)
        } catch (e: Exception) {
            println("⚠️ AiMemoryClassifier: Failed to parse assistant JSON (content: $cleanedContent), returning empty list")
            return emptyList()
        }

        val classifications = jsonResponse.classifications ?: return emptyList()

        return classifications.mapNotNull { item ->
            parseSingleClassificationItem(item, MemorySource.ASSISTANT_CONFIRMED)
        }
    }

    private fun parseConversationResponse(response: LlmResponse): ConversationPairResult {
        val cleanedContent = JsonUtils.extractJson(response.content)

        println("🔍 AiMemoryClassifier: Parsing conversation response...")
        println("   Raw content length: ${response.content.length}")
        println("   Cleaned content: $cleanedContent")

        val jsonResponse = try {
            json.decodeFromString<ConversationPairResponse>(cleanedContent)
        } catch (e: Exception) {
            println("⚠️ AiMemoryClassifier: Failed to parse conversation JSON")
            println("   Error: ${e.message}")
            println("   Cleaned content: $cleanedContent")

            val fixedContent = JsonUtils.tryFixMalformedJson(cleanedContent)
            if (fixedContent != null) {
                println("🔧 AiMemoryClassifier: Attempting to fix malformed JSON...")
                println("   Fixed content: $fixedContent")
                try {
                    json.decodeFromString<ConversationPairResponse>(fixedContent)
                } catch (e2: Exception) {
                    println("❌ AiMemoryClassifier: Failed to parse fixed JSON - ${e2.message}")
                    return ConversationPairResult(
                        userResults = emptyList(),
                        assistantResults = emptyList()
                    )
                }
            } else {
                return ConversationPairResult(
                    userResults = emptyList(),
                    assistantResults = emptyList()
                )
            }
        }

        val userResults = jsonResponse.user?.mapNotNull { item ->
            parseSingleClassificationItem(item, MemorySource.USER_EXTRACTED)
        } ?: emptyList()

        val assistantResults = jsonResponse.assistant?.mapNotNull { item ->
            parseSingleClassificationItem(item, MemorySource.ASSISTANT_CONFIRMED)
        } ?: emptyList()

        println("✅ AiMemoryClassifier: Successfully parsed conversation response")
        println("   User classifications: ${userResults.size}")
        println("   Assistant classifications: ${assistantResults.size}")

        return ConversationPairResult(
            userResults = userResults,
            assistantResults = assistantResults
        )
    }

    private fun parseSingleClassificationItem(
        item: com.example.aiadventchallenge.domain.model.SingleClassificationItem,
        source: MemorySource
    ): ClassificationResult? {
        if (item.action == "skip") {
            return null
        }

        if (item.action == "create" &&
            item.memoryType != null &&
            item.reason != null &&
            item.importance != null &&
            item.value != null) {

            return ClassificationResult.Create(
                memoryType = parseMemoryType(item.memoryType),
                reason = parseMemoryReason(item.reason),
                importance = item.importance,
                value = item.value,
                source = source,
                key = item.key
            )
        }

        return null
    }

    private fun parseMemoryType(type: String): MemoryType {
        return when (type.lowercase()) {
            "working" -> MemoryType.WORKING
            "long_term", "longterm" -> MemoryType.LONG_TERM
            else -> MemoryType.WORKING
        }
    }

    private fun parseMemoryReason(reason: String): MemoryReason {
        return try {
            MemoryReason.valueOf(reason.uppercase())
        } catch (e: Exception) {
            MemoryReason.TASK_GOAL // fallback
        }
    }

    private fun buildRequest(
        message: ChatMessage,
        workingMemory: List<MemoryEntry>,
        longTermMemory: List<MemoryEntry>
    ): MemoryClassificationRequest {
        return MemoryClassificationRequest(
            userMessage = message.content,
            existingWorkingMemory = workingMemory.map { toCompact(it) },
            existingLongTermMemory = longTermMemory.map { toCompact(it) }
        )
    }

    private fun buildAssistantRequest(
        message: ChatMessage,
        workingMemory: List<MemoryEntry>
    ): MemoryClassificationRequest {
        return MemoryClassificationRequest(
            userMessage = message.content,
            existingWorkingMemory = workingMemory.map { toCompact(it) },
            existingLongTermMemory = emptyList() // для assistant messages не используем long-term
        )
    }

    private fun buildConversationRequest(
        userMessage: ChatMessage,
        assistantMessage: ChatMessage,
        workingMemory: List<MemoryEntry>,
        longTermMemory: List<MemoryEntry>
    ): ConversationPairRequest {
        return ConversationPairRequest(
            userMessage = userMessage.content,
            assistantMessage = assistantMessage.content,
            existingWorkingMemory = workingMemory.map { toCompact(it) },
            existingLongTermMemory = longTermMemory.map { toCompact(it) }
        )
    }

    private fun toCompact(entry: MemoryEntry): MemoryEntryCompact {
        return MemoryEntryCompact(
            key = entry.key,
            value = entry.value,
            reason = entry.reason.name
        )
    }

    private fun logClassification(results: List<ClassificationResult>, metrics: MemoryClassificationMetrics) {
        if (results.isEmpty()) {
            println("🤖 AiMemoryClassifier: Classification - EMPTY")
            println("   Tokens: ${metrics.promptTokens?.plus(metrics.completionTokens ?: 0) ?: 0}")
            println("   Time: ${metrics.executionTimeMs}ms")
            return
        }

        val actionsStr = results.joinToString { r ->
            when (r) {
                is ClassificationResult.Create -> "${r.memoryType.name} (${r.reason.name})"
                is ClassificationResult.Skip -> "SKIP"
            }
        }

        println("🤖 AiMemoryClassifier: Classification - [$actionsStr]")
        println("   Count: ${results.size}")
        println("   Tokens: ${metrics.promptTokens?.plus(metrics.completionTokens ?: 0) ?: 0}")
        println("   Time: ${metrics.executionTimeMs}ms")
    }

    private fun logAssistantClassification(results: List<ClassificationResult>, metrics: MemoryClassificationMetrics) {
        if (results.isEmpty()) {
            println("🤖 AiMemoryClassifier: Assistant Classification - EMPTY")
            println("   Tokens: ${metrics.promptTokens?.plus(metrics.completionTokens ?: 0) ?: 0}")
            println("   Time: ${metrics.executionTimeMs}ms")
            return
        }

        val actionsStr = results.joinToString { r ->
            when (r) {
                is ClassificationResult.Create -> "${r.memoryType.name} (${r.reason.name})"
                is ClassificationResult.Skip -> "SKIP"
            }
        }

        println("🤖 AiMemoryClassifier: Assistant Classification - [$actionsStr]")
        println("   Count: ${results.size}")
        println("   Tokens: ${metrics.promptTokens?.plus(metrics.completionTokens ?: 0) ?: 0}")
        println("   Time: ${metrics.executionTimeMs}ms")
    }

    private fun logConversationClassification(result: ConversationPairResult, metrics: MemoryClassificationMetrics) {
        val userStr = if (result.userResults.isEmpty()) {
            "EMPTY"
        } else {
            result.userResults.joinToString { r ->
                when (r) {
                    is ClassificationResult.Create -> "${r.memoryType.name} (${r.reason.name})"
                    is ClassificationResult.Skip -> "SKIP"
                }
            }
        }

        val assistantStr = if (result.assistantResults.isEmpty()) {
            "EMPTY"
        } else {
            result.assistantResults.joinToString { r ->
                when (r) {
                    is ClassificationResult.Create -> "${r.memoryType.name} (${r.reason.name})"
                    is ClassificationResult.Skip -> "SKIP"
                }
            }
        }

        println("🤖 AiMemoryClassifier: Conversation Pair Classification")
        println("   User [$result.userResults.size]: $userStr")
        println("   Assistant [$result.assistantResults.size]: $assistantStr")
        println("   Tokens: ${metrics.promptTokens?.plus(metrics.completionTokens ?: 0) ?: 0}")
        println("   Time: ${metrics.executionTimeMs}ms")
    }
}