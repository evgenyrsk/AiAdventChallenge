package com.example.aiadventchallenge.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_settings")
data class ChatSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val strategyType: String,
    val windowSize: Int,
    val settingsJson: String? = null,
    val fitnessProfile: String? = null,
    val selectedBackend: String = "REMOTE",
    val localHost: String = "10.0.2.2",
    val localPort: Int = 11434,
    val localModel: String = "qwen2.5:3b-instruct",
    val localProfile: String = "BASELINE",
    val localTemperature: Double? = null,
    val localNumPredict: Int? = null,
    val localNumCtx: Int? = null,
    val localTopK: Int? = null,
    val localTopP: Double? = null,
    val localRepeatPenalty: Double? = null,
    val localSeed: Int? = null,
    val localStopTokens: String? = null,
    val localKeepAlive: String? = null,
    val privateServiceBaseUrl: String = "http://10.0.2.2:8085",
    val privateServiceApiKey: String = "",
    val privateServiceModel: String = "qwen2.5:3b-instruct",
    val privateServiceTimeoutMs: Long = 120000L,
    val privateServiceMaxTokens: Int? = null,
    val privateServiceContextWindow: Int? = null,
    val privateServiceTopK: Int? = null,
    val privateServiceTopP: Double? = null,
    val privateServiceRepeatPenalty: Double? = null,
    val privateServiceSeed: Int? = null,
    val privateServiceStopTokens: String? = null
)
