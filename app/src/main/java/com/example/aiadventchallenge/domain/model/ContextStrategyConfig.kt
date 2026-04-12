package com.example.aiadventchallenge.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ContextStrategyConfig(
    val type: ContextStrategyType,
    val windowSize: Int = 10,
    val settingsJson: String? = null
)
