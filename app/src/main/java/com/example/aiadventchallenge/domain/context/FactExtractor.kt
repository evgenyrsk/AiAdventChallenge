package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FactEntry
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.repository.AiRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class FactUpdateRequest(
    val existingFacts: List<FactEntry>,
    val userMessage: String
)

@Serializable
data class FactUpdateResponse(
    val facts: List<FactEntry>,
    val action: String
)

class FactExtractor(
    private val repository: AiRepository
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun extractAndUpdateFacts(
        userMessage: String,
        existingFacts: List<FactEntry>
    ): Result<List<FactEntry>> {
        return try {
            val systemPrompt = """Вы помощник по извлечению фактов.
Ваша задача - обновить факты разговора на основе сообщения пользователя.

ВАЖНО:
- Верните ТОЛЬКО валидный JSON со структурой: {"facts": [{"key": "...", "value": "..."}], "action": "update/create/delete"}
- Ключи должны быть краткими и описательными (например, "user_name", "goal", "preference")
- Значения должны быть лаконичными
- Если новые факты не найдены, верните существующие факты без изменений
- Всегда включайте все существующие факты в ответ (не удаляйте их, если они не стали неактуальными)
- Если факт больше не актуален, установите action в "delete" для этого факта
- НЕ используйте HTML, XML, Markdown или другие теги форматирования
- НЕ добавляйте никаких объяснений или дополнительного текста
- Ответ должен начинаться с { и заканчиваться }

Формат ответа (строгий JSON):
{
  "facts": [
    {"key": "user_name", "value": "Иван"},
    {"key": "goal", "value": "изучить Android"}
  ],
  "action": "update"
}"""

            val prompt = """Извлеките или обновите факты из сообщения пользователя.

Существующие факты:
${existingFacts.joinToString("\n") { "- ${it.key}: ${it.value}" }}

Сообщение пользователя:
$userMessage

Верните обновленные факты в формате JSON."""

            val config = RequestConfig(
                systemPrompt = systemPrompt,
                temperature = 0.1
            )

            when (val result = repository.askWithUsage(prompt, null, config)) {
                is ChatResult.Success -> {
                    val responseContent = result.data.content
                    parseFactResponse(responseContent, existingFacts)
                }
                is ChatResult.Error -> {
                    Result.failure(Exception("Failed to extract facts: ${result.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFactResponse(
        response: String,
        existingFacts: List<FactEntry>
    ): Result<List<FactEntry>> {
        return try {
            if (response.trim().startsWith("<")) {
                println("AI returned HTML/XML instead of JSON. Response: $response")
                return Result.success(existingFacts)
            }

            val cleanedResponse = response
                .trim()
                .let { if (it.startsWith("```")) it.substringAfter("```").substringBefore("```") else it }
                .trim()

            val factResponse = json.decodeFromString<FactUpdateResponse>(cleanedResponse)

            val updatedFacts = when (factResponse.action) {
                "delete" -> emptyList()
                "update", "create" -> factResponse.facts.map { fact ->
                    fact.copy(
                        source = FactEntry.FactSource.EXTRACTED,
                        updatedAt = System.currentTimeMillis()
                    )
                }
                else -> existingFacts
            }

            Result.success(updatedFacts)
        } catch (e: Exception) {
            println("Failed to parse fact response: ${e.message}")
            Result.success(existingFacts)
        }
    }
}
