package com.example.aiadventchallenge.domain.model

data class ChatSettingsPayload(
    val strategyType: ContextStrategyType,
    val windowSize: Int,
    val fitnessProfile: FitnessProfileType,
    val backendSettings: AiBackendSettings
)
