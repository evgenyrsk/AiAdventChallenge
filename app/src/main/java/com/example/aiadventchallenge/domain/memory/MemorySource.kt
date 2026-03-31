package com.example.aiadventchallenge.domain.memory

enum class MemorySource {
    USER_EXTRACTED,      // извлечено из user message
    ASSISTANT_CONFIRMED, // подтверждено в assistant message
    SYSTEM               // системная запись
}