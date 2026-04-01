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
    val newTaskDetected: Boolean,
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
    val assistant: List<SingleClassificationItem>? = null,
    val new_task_detected: Boolean = false
)

data class ConversationPairResult(
    val userResults: List<ClassificationResult>,
    val assistantResults: List<ClassificationResult>,
    val newTaskDetected: Boolean = false
)

class AiMemoryClassifier(
    private val repository: AiRepository,
    private val config: AiClassifierConfig = AiClassifierConfig()
) {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
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
                    newTaskDetected = pairResult.newTaskDetected,
                    metrics = metrics
                )
            )

        } catch (e: Exception) {
            println("❌ AiMemoryClassifier: Error classifying conversation pair - ${e.message}")
            Result.failure(e)
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
15. Каждая классификация в массиве обрабатывается independently
16. ОПРЕДЕЛИТЕ new_task_detected (true/false) - пользователь начал НОВУЮ задачу:
    - true: "Теперь другая задача", "Перейдём к новой теме", "Есть другой вопрос", цель изменилась радикально
    - false: уточнение текущей задачи, добавление ограничений, продолжение обсуждения

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
  ],
  "new_task_detected": false
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
  "assistant": [],
  "new_task_detected": false
}

3. Новая задача:
{
  "user": [],
  "assistant": [],
  "new_task_detected": true
}

4. Пустой результат без смены задачи:
{
  "user": [],
  "assistant": [],
  "new_task_detected": false
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
  ],
  "new_task_detected": true | false
}"""
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
                        assistantResults = emptyList(),
                        newTaskDetected = false
                    )
                }
            } else {
                return ConversationPairResult(
                    userResults = emptyList(),
                    assistantResults = emptyList(),
                    newTaskDetected = false
                )
            }
        }

        val userResults = jsonResponse.user?.mapNotNull { item ->
            parseSingleClassificationItem(item, MemorySource.USER_EXTRACTED)
        } ?: emptyList()

        val assistantResults = jsonResponse.assistant?.mapNotNull { item ->
            parseSingleClassificationItem(item, MemorySource.ASSISTANT_CONFIRMED)
        } ?: emptyList()

        val newTaskDetected = jsonResponse.new_task_detected ?: false

        println("✅ AiMemoryClassifier: Successfully parsed conversation response")
        println("   User classifications: ${userResults.size}")
        println("   Assistant classifications: ${assistantResults.size}")
        println("   New task detected: $newTaskDetected")

        return ConversationPairResult(
            userResults = userResults,
            assistantResults = assistantResults,
            newTaskDetected = newTaskDetected
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