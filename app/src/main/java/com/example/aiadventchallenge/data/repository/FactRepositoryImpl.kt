package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.FactDao
import com.example.aiadventchallenge.data.local.entity.FactEntity
import com.example.aiadventchallenge.domain.model.FactEntry
import com.example.aiadventchallenge.domain.repository.FactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FactRepositoryImpl(
    private val factDao: FactDao
) : FactRepository {

    override fun getAllFacts(): Flow<List<FactEntry>> {
        return factDao.getAllFacts().map { entities ->
            entities.map { entity ->
                FactEntry(
                    key = entity.key,
                    value = entity.value,
                    source = when (entity.source) {
                        "EXTRACTED" -> FactEntry.FactSource.EXTRACTED
                        "MANUAL" -> FactEntry.FactSource.MANUAL
                        "SYSTEM" -> FactEntry.FactSource.SYSTEM
                        else -> FactEntry.FactSource.EXTRACTED
                    },
                    updatedAt = entity.updatedAt,
                    confidence = entity.confidence,
                    isOptional = entity.isOptional
                )
            }
        }
    }

    override suspend fun getFactByKey(key: String): FactEntry? {
        return factDao.getFactByKey(key)?.let { entity ->
            FactEntry(
                key = entity.key,
                value = entity.value,
                source = when (entity.source) {
                    "EXTRACTED" -> FactEntry.FactSource.EXTRACTED
                    "MANUAL" -> FactEntry.FactSource.MANUAL
                    "SYSTEM" -> FactEntry.FactSource.SYSTEM
                    else -> FactEntry.FactSource.EXTRACTED
                },
                updatedAt = entity.updatedAt,
                confidence = entity.confidence,
                isOptional = entity.isOptional
            )
        }
    }

    override suspend fun insertFact(fact: FactEntry) {
        val entity = FactEntity(
            key = fact.key,
            value = fact.value,
            source = fact.source.name,
            updatedAt = fact.updatedAt,
            confidence = fact.confidence,
            isOptional = fact.isOptional
        )
        factDao.insertFact(entity)
    }

    override suspend fun updateFact(fact: FactEntry) {
        val entity = FactEntity(
            key = fact.key,
            value = fact.value,
            source = fact.source.name,
            updatedAt = fact.updatedAt,
            confidence = fact.confidence,
            isOptional = fact.isOptional
        )
        factDao.updateFact(entity)
    }

    override suspend fun deleteFact(key: String) {
        factDao.deleteFact(key)
    }

    override suspend fun clearAllFacts() {
        factDao.clearAllFacts()
    }
}
