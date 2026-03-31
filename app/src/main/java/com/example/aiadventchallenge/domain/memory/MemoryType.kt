package com.example.aiadventchallenge.domain.memory

enum class MemoryType {
    SHORT_TERM,   // последние сообщения (хранятся в chat_messages, берутся takeLast)
    WORKING,      // данные текущей задачи (хранятся в memory_entries)
    LONG_TERM     // устойчивые факты (хранятся в memory_entries)
}