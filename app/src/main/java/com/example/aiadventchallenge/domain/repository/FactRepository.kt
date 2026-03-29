package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.model.FactEntry
import kotlinx.coroutines.flow.Flow

interface FactRepository {
    fun getAllFacts(): Flow<List<FactEntry>>

    suspend fun getFactByKey(key: String): FactEntry?

    suspend fun insertFact(fact: FactEntry)

    suspend fun updateFact(fact: FactEntry)

    suspend fun deleteFact(key: String)

    suspend fun clearAllFacts()
}
