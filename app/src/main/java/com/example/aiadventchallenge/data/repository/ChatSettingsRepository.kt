package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.BuildConfig
import com.example.aiadventchallenge.data.local.dao.ChatSettingsDao
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.ChatSettingsPayload
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.LocalLlmProfile
import com.example.aiadventchallenge.domain.model.LocalLlmRuntimeOptions
import com.example.aiadventchallenge.domain.model.PrivateAiServiceConfig
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
                ),
                profile = runCatching {
                    LocalLlmProfile.valueOf(entity.localProfile)
                }.getOrDefault(LocalLlmProfile.BASELINE),
                runtimeOptions = LocalLlmRuntimeOptions(
                    temperature = entity.localTemperature,
                    numPredict = entity.localNumPredict,
                    numCtx = entity.localNumCtx,
                    topK = entity.localTopK,
                    topP = entity.localTopP,
                    repeatPenalty = entity.localRepeatPenalty,
                    seed = entity.localSeed,
                    stop = entity.localStopTokens
                        ?.split('\n')
                        ?.map(String::trim)
                        ?.filter(String::isNotBlank)
                        ?.takeIf { it.isNotEmpty() },
                    keepAlive = entity.localKeepAlive
                )
            ),
            privateServiceConfig = PrivateAiServiceConfig(
                baseUrl = entity.privateServiceBaseUrl.ifBlank { BuildConfig.PRIVATE_AI_SERVICE_BASE_URL },
                apiKey = entity.privateServiceApiKey.ifBlank { BuildConfig.PRIVATE_AI_SERVICE_API_KEY },
                model = entity.privateServiceModel.ifBlank { BuildConfig.PRIVATE_AI_SERVICE_MODEL },
                timeoutMs = entity.privateServiceTimeoutMs,
                maxTokens = entity.privateServiceMaxTokens,
                contextWindow = entity.privateServiceContextWindow,
                topK = entity.privateServiceTopK,
                topP = entity.privateServiceTopP,
                repeatPenalty = entity.privateServiceRepeatPenalty,
                seed = entity.privateServiceSeed,
                stop = entity.privateServiceStopTokens
                    ?.split('\n')
                    ?.map(String::trim)
                    ?.filter(String::isNotBlank)
                    ?.takeIf { it.isNotEmpty() }
            )
        )
    }

    override suspend fun updateAiBackendSettings(settings: AiBackendSettings) {
        val existing = chatSettingsDao.getSettings()
        val entity = (existing ?: defaultEntity()).copy(
            selectedBackend = settings.selectedBackend.name,
            localHost = settings.localConfig.host,
            localPort = settings.localConfig.port,
            localModel = settings.localConfig.model,
            localProfile = settings.localConfig.profile.name,
            localTemperature = settings.localConfig.runtimeOptions.temperature,
            localNumPredict = settings.localConfig.runtimeOptions.numPredict,
            localNumCtx = settings.localConfig.runtimeOptions.numCtx,
            localTopK = settings.localConfig.runtimeOptions.topK,
            localTopP = settings.localConfig.runtimeOptions.topP,
            localRepeatPenalty = settings.localConfig.runtimeOptions.repeatPenalty,
            localSeed = settings.localConfig.runtimeOptions.seed,
            localStopTokens = settings.localConfig.runtimeOptions.stop?.joinToString("\n"),
            localKeepAlive = settings.localConfig.runtimeOptions.keepAlive,
            privateServiceBaseUrl = settings.privateServiceConfig.baseUrl,
            privateServiceApiKey = settings.privateServiceConfig.apiKey,
            privateServiceModel = settings.privateServiceConfig.model,
            privateServiceTimeoutMs = settings.privateServiceConfig.timeoutMs,
            privateServiceMaxTokens = settings.privateServiceConfig.maxTokens,
            privateServiceContextWindow = settings.privateServiceConfig.contextWindow,
            privateServiceTopK = settings.privateServiceConfig.topK,
            privateServiceTopP = settings.privateServiceConfig.topP,
            privateServiceRepeatPenalty = settings.privateServiceConfig.repeatPenalty,
            privateServiceSeed = settings.privateServiceConfig.seed,
            privateServiceStopTokens = settings.privateServiceConfig.stop?.joinToString("\n")
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
            localModel = payload.backendSettings.localConfig.model,
            localProfile = payload.backendSettings.localConfig.profile.name,
            localTemperature = payload.backendSettings.localConfig.runtimeOptions.temperature,
            localNumPredict = payload.backendSettings.localConfig.runtimeOptions.numPredict,
            localNumCtx = payload.backendSettings.localConfig.runtimeOptions.numCtx,
            localTopK = payload.backendSettings.localConfig.runtimeOptions.topK,
            localTopP = payload.backendSettings.localConfig.runtimeOptions.topP,
            localRepeatPenalty = payload.backendSettings.localConfig.runtimeOptions.repeatPenalty,
            localSeed = payload.backendSettings.localConfig.runtimeOptions.seed,
            localStopTokens = payload.backendSettings.localConfig.runtimeOptions.stop?.joinToString("\n"),
            localKeepAlive = payload.backendSettings.localConfig.runtimeOptions.keepAlive,
            privateServiceBaseUrl = payload.backendSettings.privateServiceConfig.baseUrl,
            privateServiceApiKey = payload.backendSettings.privateServiceConfig.apiKey,
            privateServiceModel = payload.backendSettings.privateServiceConfig.model,
            privateServiceTimeoutMs = payload.backendSettings.privateServiceConfig.timeoutMs,
            privateServiceMaxTokens = payload.backendSettings.privateServiceConfig.maxTokens,
            privateServiceContextWindow = payload.backendSettings.privateServiceConfig.contextWindow,
            privateServiceTopK = payload.backendSettings.privateServiceConfig.topK,
            privateServiceTopP = payload.backendSettings.privateServiceConfig.topP,
            privateServiceRepeatPenalty = payload.backendSettings.privateServiceConfig.repeatPenalty,
            privateServiceSeed = payload.backendSettings.privateServiceConfig.seed,
            privateServiceStopTokens = payload.backendSettings.privateServiceConfig.stop?.joinToString("\n")
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
