package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.MemoryClassificationDao
import com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import kotlinx.coroutines.flow.Flow

class MemoryClassificationRepositoryImpl(
    private val memoryClassificationDao: MemoryClassificationDao
) : MemoryClassificationRepository {

    override suspend fun saveClassificationMetrics(entity: MemoryClassificationEntity) {
        memoryClassificationDao.insertClassification(entity)
    }

    override fun getClassificationLogs(branchId: String): Flow<List<MemoryClassificationEntity>> {
        return memoryClassificationDao.getRecentClassifications(branchId)
    }

    override fun getAllRecentClassifications(): Flow<List<MemoryClassificationEntity>> {
        return memoryClassificationDao.getAllRecentClassifications()
    }

    override suspend fun clearClassifications(branchId: String) {
        memoryClassificationDao.clearByBranch(branchId)
    }

    override suspend fun clearAll() {
        memoryClassificationDao.clearAll()
    }
}