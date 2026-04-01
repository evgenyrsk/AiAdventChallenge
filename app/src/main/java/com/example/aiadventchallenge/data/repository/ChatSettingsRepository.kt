package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ChatSettingsDao
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository

class ChatSettingsRepository(
    private val chatSettingsDao: ChatSettingsDao
) : com.example.aiadventchallenge.domain.repository.ChatSettingsRepository {

    override suspend fun getSettings(): ContextStrategyConfig {
        val entity = chatSettingsDao.getSettings()
        return if (entity != null) {
            ContextStrategyConfig(
                type = ContextStrategyType.valueOf(entity.strategyType),
                windowSize = entity.windowSize,
                settingsJson = entity.settingsJson
            )
        } else {
            ContextStrategyConfig(
                type = ContextStrategyType.SLIDING_WINDOW,
                windowSize = 10
            )
        }
    }

    override suspend fun updateSettings(config: ContextStrategyConfig) {
        val entity = ChatSettingsEntity(
            id = 1,
            strategyType = config.type.name,
            windowSize = config.windowSize,
            settingsJson = config.settingsJson
        )
        chatSettingsDao.insertSettings(entity)
    }

    override suspend fun getStrategyType(): ContextStrategyType {
        val entity = chatSettingsDao.getSettings()
        return if (entity != null) {
            ContextStrategyType.valueOf(entity.strategyType)
        } else {
            ContextStrategyType.SLIDING_WINDOW
        }
    }

    override suspend fun updateStrategyType(type: ContextStrategyType) {
        val existing = chatSettingsDao.getSettings()
        val entity = if (existing != null) {
            existing.copy(strategyType = type.name)
        } else {
            ChatSettingsEntity(
                id = 1,
                strategyType = type.name,
                windowSize = 10
            )
        }
        chatSettingsDao.insertSettings(entity)
    }

    override suspend fun getWindowSize(): Int {
        val entity = chatSettingsDao.getSettings()
        return entity?.windowSize ?: 10
    }

    override suspend fun updateWindowSize(windowSize: Int) {
        val existing = chatSettingsDao.getSettings()
        val entity = if (existing != null) {
            existing.copy(windowSize = windowSize)
        } else {
            ChatSettingsEntity(
                id = 1,
                strategyType = ContextStrategyType.SLIDING_WINDOW.name,
                windowSize = windowSize
            )
        }
        chatSettingsDao.insertSettings(entity)
    }

    override suspend fun getFitnessProfile(): FitnessProfileType {
        val entity = chatSettingsDao.getSettings()
        return if (entity?.fitnessProfile != null) {
            try {
                FitnessProfileType.valueOf(entity.fitnessProfile)
            } catch (e: IllegalArgumentException) {
                FitnessProfileType.INTERMEDIATE
            }
        } else {
            FitnessProfileType.INTERMEDIATE
        }
    }

    override suspend fun setFitnessProfile(profile: FitnessProfileType) {
        val existing = chatSettingsDao.getSettings()
        val entity = if (existing != null) {
            existing.copy(fitnessProfile = profile.name)
        } else {
            ChatSettingsEntity(
                id = 1,
                strategyType = ContextStrategyType.SLIDING_WINDOW.name,
                windowSize = 10,
                fitnessProfile = profile.name
            )
        }
        chatSettingsDao.insertSettings(entity)
    }
}
