package com.example.privateaiservice.service

import com.example.privateaiservice.api.model.GatewayChatRequest
import com.example.privateaiservice.config.PrivateAiServiceConfig

class RequestValidationService(
    private val config: PrivateAiServiceConfig
) {
    fun validate(request: GatewayChatRequest) {
        if (request.messages.isEmpty()) {
            throw ValidationException("messages must not be empty")
        }
        if (request.messages.size > config.maxMessages) {
            throw ValidationException("messages exceed max allowed count of ${config.maxMessages}")
        }

        val totalChars = request.messages.sumOf { it.content.length }
        if (totalChars > config.maxInputChars) {
            throw ValidationException("input is too large: $totalChars chars exceeds ${config.maxInputChars}")
        }

        request.maxTokens?.let {
            if (it <= 0 || it > config.maxOutputTokens) {
                throw ValidationException("maxTokens must be between 1 and ${config.maxOutputTokens}")
            }
        }

        request.contextWindow?.let {
            if (it <= 0 || it > config.maxContextWindow) {
                throw ValidationException("contextWindow must be between 1 and ${config.maxContextWindow}")
            }
        }
    }
}
