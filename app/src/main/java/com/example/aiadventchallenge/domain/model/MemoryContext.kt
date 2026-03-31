package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.memory.MemoryEntry

data class MemoryContext(
    val workingMemory: List<MemoryEntry>,
    val longTermMemory: List<MemoryEntry>
)