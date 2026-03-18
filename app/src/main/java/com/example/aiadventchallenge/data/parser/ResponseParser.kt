package com.example.aiadventchallenge.data.parser

import com.example.aiadventchallenge.data.JsonConfig
import com.example.aiadventchallenge.data.model.ChatResponse
import com.example.aiadventchallenge.data.model.ErrorResponse

class ResponseParser {
    fun parse(responseBody: String): String {
        return try {
            val parsed = JsonConfig.json.decodeFromString(ChatResponse.serializer(), responseBody)
            val choice = parsed.choices.firstOrNull()
            val message = choice?.message

            when {
                !message?.content.isNullOrBlank() -> {
                    message.content.trim()
                }
                !message?.reasoning.isNullOrBlank() -> {
                    "Model returned reasoning but no final answer. " +
                            "finish_reason=${choice.finishReason}, reasoning=${message.reasoning}"
                }
                else -> {
                    "Empty response"
                }
            }
        } catch (e: Exception) {
            "Error parsing response: ${e.message ?: "Unknown parsing error"}"
        }
    }

    fun parseError(code: Int, responseBody: String): String {
        return try {
            val errorResponse = JsonConfig.json.decodeFromString(ErrorResponse.serializer(), responseBody)
            "Error: ${errorResponse.error?.message ?: "HTTP $code"}"
        } catch (_: Exception) {
            "Error: HTTP $code: $responseBody"
        }
    }
}
