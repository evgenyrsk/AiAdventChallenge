package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity
import kotlinx.coroutines.flow.Flow

interface MemoryClassificationRepository {
    suspend fun saveClassificationMetrics(entity: MemoryClassificationEntity)
    
    fun getClassificationLogs(branchId: String): Flow<List<MemoryClassificationEntity>>
    
    fun getAllRecentClassifications(): Flow<List<MemoryClassificationEntity>>
    
    suspend fun clearClassifications(branchId: String)
    
    suspend fun clearAll()
}