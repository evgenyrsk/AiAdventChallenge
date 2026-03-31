package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.MemoryDao
import com.example.aiadventchallenge.data.local.entity.MemoryEntity
import com.example.aiadventchallenge.domain.memory.MemoryEntry
import com.example.aiadventchallenge.domain.memory.MemoryReason
import com.example.aiadventchallenge.domain.memory.MemorySource
import com.example.aiadventchallenge.domain.memory.MemoryType
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao
) : MemoryRepository {

    override fun getWorkingMemory(branchId: String): Flow<List<MemoryEntry>> {
        return memoryDao.getActiveEntriesByType(MemoryType.WORKING.name, branchId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getLongTermMemory(branchId: String): Flow<List<MemoryEntry>> {
        return memoryDao.getActiveEntriesByType(MemoryType.LONG_TERM.name, branchId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getAllEntriesByBranch(branchId: String): Flow<List<MemoryEntry>> {
        return memoryDao.getAllEntriesByBranch(branchId)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override suspend fun insertEntry(entry: MemoryEntry) {
        memoryDao.insertEntry(entry.toEntity())
    }

    override suspend fun updateEntry(entry: MemoryEntry) {
        memoryDao.updateEntry(entry.toEntity())
    }

    override suspend fun deactivateEntry(id: String) {
        memoryDao.deactivateEntry(id, System.currentTimeMillis())
    }

    override suspend fun deactivateExpiredEntries() {
        memoryDao.deactivateExpiredEntries()
    }

    override suspend fun getEntryById(id: String): MemoryEntry? {
        return memoryDao.getEntryById(id)?.toDomainModel()
    }

    override suspend fun getEntryByKey(key: String, branchId: String): MemoryEntry? {
        return memoryDao.getEntryByKey(key, branchId)?.toDomainModel()
    }

    override suspend fun clearWorkingMemory(branchId: String) {
        memoryDao.clearByBranchAndType(branchId, MemoryType.WORKING.name)
    }

    override suspend fun clearLongTermMemory(branchId: String) {
        memoryDao.clearByBranchAndType(branchId, MemoryType.LONG_TERM.name)
    }

    override suspend fun clearAllMemory(branchId: String) {
        memoryDao.clearByBranch(branchId)
    }

    override suspend fun getTotalCountByBranch(branchId: String): Int {
        return memoryDao.getTotalCountByBranch(branchId)
    }

    override suspend fun getActiveCountByBranch(branchId: String): Int {
        return memoryDao.getActiveCountByBranch(branchId)
    }
}

private fun MemoryEntity.toDomainModel(): MemoryEntry {
    return MemoryEntry(
        id = id,
        key = key,
        value = value,
        memoryType = MemoryType.valueOf(memoryType),
        reason = MemoryReason.valueOf(reason),
        source = MemorySource.valueOf(source),
        importance = importance,
        branchId = branchId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        ttl = ttl
    )
}

private fun MemoryEntry.toEntity(): MemoryEntity {
    return MemoryEntity(
        id = id,
        key = key,
        value = value,
        memoryType = memoryType.name,
        reason = reason.name,
        source = source.name,
        importance = importance,
        branchId = branchId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isActive = isActive,
        ttl = ttl
    )
}