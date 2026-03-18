package com.example.aiadventchallenge.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    companion object {
        fun fromValue(value: String): MessageRole =
            entries.find { it.value == value } ?: USER
    }
}

@Serializable
data class Message(
    val role: String,
    val content: String,
) {
    constructor(role: MessageRole, content: String) : this(role.value, content)
}
