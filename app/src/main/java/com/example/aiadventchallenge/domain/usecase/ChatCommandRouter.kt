package com.example.aiadventchallenge.domain.usecase

sealed interface ChatCommand {
    data object None : ChatCommand
    data class Help(val question: String?) : ChatCommand
}

class ChatCommandRouter {
    fun route(rawInput: String): ChatCommand {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith("/help")) return ChatCommand.None

        val suffix = trimmed.removePrefix("/help").trim()
        return ChatCommand.Help(suffix.ifBlank { null })
    }
}
