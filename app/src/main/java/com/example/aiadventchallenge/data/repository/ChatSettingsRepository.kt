package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ChatSettingsDao
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.ChatSettingsPayload
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository

class ChatSettingsRepository(
    private val chatSettingsDao: ChatSettingsDao
) : com.example.aiadventchallenge.domain.repository.ChatSettingsRepository {

    private val legacyDefaultLocalModel = "llama3.2"
    private val currentDefaultLocalModel = LocalLlmConfig().model

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
        val existing = chatSettingsDao.getSettings()
        chatSettingsDao.insertSettings(
            (existing ?: defaultEntity()).copy(
                strategyType = config.type.name,
                windowSize = config.windowSize,
                settingsJson = config.settingsJson
            )
        )
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
            defaultEntity().copy(strategyType = type.name)
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
            defaultEntity().copy(windowSize = windowSize)
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
            defaultEntity().copy(fitnessProfile = profile.name)
        }
        chatSettingsDao.insertSettings(entity)
    }

    override suspend fun getAiBackendSettings(): AiBackendSettings {
        val entity = chatSettingsDao.getSettings()
        if (entity == null) {
            return AiBackendSettings()
        }

        val selectedBackend = runCatching {
            AiBackendType.valueOf(entity.selectedBackend)
        }.getOrDefault(AiBackendType.REMOTE)

        return AiBackendSettings(
            selectedBackend = selectedBackend,
            localConfig = LocalLlmConfig(
                host = entity.localHost,
                port = entity.localPort,
                model = normalizeLocalModel(
                    selectedBackend = selectedBackend,
                    model = entity.localModel
                )
            )
        )
    }

    override suspend fun updateAiBackendSettings(settings: AiBackendSettings) {
        val existing = chatSettingsDao.getSettings()
        val entity = (existing ?: defaultEntity()).copy(
            selectedBackend = settings.selectedBackend.name,
            localHost = settings.localConfig.host,
            localPort = settings.localConfig.port,
            localModel = settings.localConfig.model
        )
        chatSettingsDao.insertSettings(entity)
    }

    override suspend fun applyChatSettings(payload: ChatSettingsPayload) {
        val existing = chatSettingsDao.getSettings()
        val entity = (existing ?: defaultEntity()).copy(
            strategyType = payload.strategyType.name,
            windowSize = payload.windowSize,
            fitnessProfile = payload.fitnessProfile.name,
            selectedBackend = payload.backendSettings.selectedBackend.name,
            localHost = payload.backendSettings.localConfig.host,
            localPort = payload.backendSettings.localConfig.port,
            localModel = payload.backendSettings.localConfig.model
        )
        chatSettingsDao.insertSettings(entity)
    }

    private fun defaultEntity(): ChatSettingsEntity {
        return ChatSettingsEntity(
            id = 1,
            strategyType = ContextStrategyType.SLIDING_WINDOW.name,
            windowSize = 10
        )
    }

    private fun normalizeLocalModel(selectedBackend: AiBackendType, model: String): String {
        if (selectedBackend != AiBackendType.LOCAL_OLLAMA) {
            return model
        }

        val normalized = model.trim()
        return if (normalized.isBlank() || normalized == legacyDefaultLocalModel) {
            currentDefaultLocalModel
        } else {
            normalized
        }
    }
}
