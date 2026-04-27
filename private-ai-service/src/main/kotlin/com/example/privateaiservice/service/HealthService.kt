package com.example.privateaiservice.service

import com.example.privateaiservice.api.model.HealthResponse
import com.example.privateaiservice.config.PrivateAiServiceConfig

class HealthService(
    private val config: PrivateAiServiceConfig,
    private val ollamaGatewayService: OllamaGatewayService
) {
    fun getHealth(): HealthResponse {
        val ollama = ollamaGatewayService.health()
        return HealthResponse(
            status = if (ollama.available) "ok" else "degraded",
            ollamaAvailable = ollama.available,
            model = ollama.model,
            ollamaBaseUrl = config.ollamaBaseUrl
        )
    }
}
