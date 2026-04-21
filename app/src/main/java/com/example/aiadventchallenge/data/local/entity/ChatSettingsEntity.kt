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
    val localModel: String = "qwen2.5:3b-instruct"
)
