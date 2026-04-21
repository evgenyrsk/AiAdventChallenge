package com.example.aiadventchallenge.domain.model

data class AiBackendSettings(
    val selectedBackend: AiBackendType = AiBackendType.REMOTE,
    val localConfig: LocalLlmConfig = LocalLlmConfig()
)
