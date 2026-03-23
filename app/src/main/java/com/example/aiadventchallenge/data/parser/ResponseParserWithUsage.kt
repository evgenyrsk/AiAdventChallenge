package com.example.aiadventchallenge.data.parser

import com.example.aiadventchallenge.data.config.JsonConfig
import com.example.aiadventchallenge.data.model.ChatResponse

class ResponseParserWithUsage {
    fun parse(responseBody: String): ParsedResponse {
        return try {
            val parsed = JsonConfig.json.decodeFromString(ChatResponse.serializer(), responseBody)
            val choice = parsed.choices.firstOrNull()
            val message = choice?.message
            val usage = parsed.usage

            val content = when {
                !message?.content.isNullOrBlank() -> message.content.trim()
                else -> "Empty response"
            }

            ParsedResponse(
                content = content,
                promptTokens = usage?.promptTokens,
                completionTokens = usage?.completionTokens,
                totalTokens = usage?.totalTokens
            )
        } catch (e: Exception) {
            ParsedResponse(
                content = "Error parsing response: ${e.message ?: "Unknown parsing error"}",
                promptTokens = null,
                completionTokens = null,
                totalTokens = null
            )
        }
    }

    fun parseError(code: Int, responseBody: String): String {
        return try {
            val errorResponse = JsonConfig.json.decodeFromString(
                com.example.aiadventchallenge.data.model.ErrorResponse.serializer(), 
                responseBody
            )
            "Error: ${errorResponse.error?.message ?: "HTTP $code"}"
        } catch (_: Exception) {
            "Error: HTTP $code"
        }
    }

    data class ParsedResponse(
        val content: String,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?
    )
}
