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
            val systemPrompt = """You are a fact extraction assistant. 
Your task is to update the conversation facts based on the user's message.

IMPORTANT:
- Return ONLY valid JSON with this structure: {"facts": [{"key": "...", "value": "..."}], "action": "update/create/delete"}
- Keys should be short and descriptive (e.g., "user_name", "goal", "preference")
- Values should be concise
- If no new facts are found, return the existing facts unchanged
- Always include all existing facts in the response (don't remove them unless they become irrelevant)
- If a fact is no longer relevant, set action to "delete" for that fact

Response format (strict JSON):
{
  "facts": [
    {"key": "fact_key", "value": "fact_value", "source": "EXTRACTED", "updatedAt": 1234567890, "confidence": 0.9, "isOptional": false}
  ],
  "action": "update"
}"""

            val prompt = """Extract or update facts from the user message.

Existing facts:
${existingFacts.joinToString("\n") { "- ${it.key}: ${it.value}" }}

User message:
$userMessage

Return updated facts as JSON."""

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
