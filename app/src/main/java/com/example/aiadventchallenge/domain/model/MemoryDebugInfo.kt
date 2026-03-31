package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.memory.MemoryEntry

data class MemoryDebugInfo(
    val shortTermMessages: List<ChatMessage>,
    val workingMemory: List<MemoryEntry>,
    val longTermMemory: List<MemoryEntry>,
    val assembledContext: List<String>,
    val memoryStats: MemoryStats
)

data class MemoryStats(
    val totalWorkingEntries: Int,
    val activeWorkingEntries: Int,
    val totalLongTermEntries: Int,
    val activeLongTermEntries: Int
)