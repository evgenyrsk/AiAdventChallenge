package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.memory.MemoryEntry
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getWorkingMemory(branchId: String): Flow<List<MemoryEntry>>
    fun getLongTermMemory(branchId: String): Flow<List<MemoryEntry>>
    fun getAllEntriesByBranch(branchId: String): Flow<List<MemoryEntry>>

    suspend fun insertEntry(entry: MemoryEntry)
    suspend fun updateEntry(entry: MemoryEntry)
    suspend fun deactivateEntry(id: String)
    suspend fun deactivateExpiredEntries()

    suspend fun getEntryById(id: String): MemoryEntry?
    suspend fun getEntryByKey(key: String, branchId: String): MemoryEntry?

    suspend fun clearWorkingMemory(branchId: String)
    suspend fun clearLongTermMemory(branchId: String)
    suspend fun clearAllMemory(branchId: String)

    suspend fun getTotalCountByBranch(branchId: String): Int
    suspend fun getActiveCountByBranch(branchId: String): Int
}