package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ChatSettingsDao
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.ChatSettingsPayload
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.LocalLlmProfile
import com.example.aiadventchallenge.domain.model.LocalLlmRuntimeOptions
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatSettingsRepositoryTest {

    private val dao = mockk<ChatSettingsDao>(relaxed = true)
    private val repository = ChatSettingsRepository(dao)

    @Test
    fun `getAiBackendSettings reads persisted backend config`() = runTest {
        coEvery { dao.getSettings() } returns ChatSettingsEntity(
            id = 1,
            strategyType = "SLIDING_WINDOW",
            windowSize = 10,
            selectedBackend = "LOCAL_OLLAMA",
            localHost = "localhost",
            localPort = 11434,
            localModel = "qwen2.5",
            localProfile = "OPTIMIZED_CHAT",
            localTemperature = 0.2,
            localNumCtx = 4096
        )

        val settings = repository.getAiBackendSettings()

        assertEquals(AiBackendType.LOCAL_OLLAMA, settings.selectedBackend)
        assertEquals("localhost", settings.localConfig.host)
        assertEquals(11434, settings.localConfig.port)
        assertEquals("qwen2.5", settings.localConfig.model)
        assertEquals(LocalLlmProfile.OPTIMIZED_CHAT, settings.localConfig.profile)
        assertEquals(0.2, settings.localConfig.runtimeOptions.temperature)
        assertEquals(4096, settings.localConfig.runtimeOptions.numCtx)
    }

    @Test
    fun `updateAiBackendSettings stores backend config alongside existing settings`() = runTest {
        coEvery { dao.getSettings() } returns ChatSettingsEntity(
            id = 1,
            strategyType = "BRANCHING",
            windowSize = 20,
            fitnessProfile = "EXPERT"
        )

        repository.updateAiBackendSettings(
            AiBackendSettings(
                selectedBackend = AiBackendType.LOCAL_OLLAMA,
                localConfig = LocalLlmConfig(
                    host = "localhost",
                    port = 11434,
                    model = "qwen2.5:3b-instruct",
                    profile = LocalLlmProfile.OPTIMIZED_RAG,
                    runtimeOptions = LocalLlmRuntimeOptions(
                        temperature = 0.1,
                        numPredict = 280,
                        numCtx = 6144
                    )
                )
            )
        )

        coVerify {
            dao.insertSettings(
                match {
                    it.strategyType == "BRANCHING" &&
                        it.windowSize == 20 &&
                        it.fitnessProfile == "EXPERT" &&
                        it.selectedBackend == "LOCAL_OLLAMA" &&
                        it.localHost == "localhost" &&
                        it.localPort == 11434 &&
                        it.localModel == "qwen2.5:3b-instruct" &&
                        it.localProfile == "OPTIMIZED_RAG" &&
                        it.localTemperature == 0.1 &&
                        it.localNumPredict == 280 &&
                        it.localNumCtx == 6144
                }
            )
        }
    }

    @Test
    fun `applyChatSettings stores all preferences atomically`() = runTest {
        coEvery { dao.getSettings() } returns ChatSettingsEntity(
            id = 1,
            strategyType = "SLIDING_WINDOW",
            windowSize = 10,
            fitnessProfile = "BEGINNER",
            selectedBackend = "REMOTE"
        )

        repository.applyChatSettings(
            ChatSettingsPayload(
                strategyType = ContextStrategyType.STICKY_FACTS,
                windowSize = 25,
                fitnessProfile = FitnessProfileType.EXPERT,
                backendSettings = AiBackendSettings(
                    selectedBackend = AiBackendType.LOCAL_OLLAMA,
                    localConfig = LocalLlmConfig(
                        host = "localhost",
                        port = 11434,
                        model = "qwen2.5:3b-instruct",
                        profile = LocalLlmProfile.OPTIMIZED_CHAT,
                        runtimeOptions = LocalLlmRuntimeOptions(
                            temperature = 0.2,
                            topK = 40,
                            stop = listOf("END")
                        )
                    )
                )
            )
        )

        coVerify {
            dao.insertSettings(
                match {
                    it.strategyType == "STICKY_FACTS" &&
                        it.windowSize == 25 &&
                        it.fitnessProfile == "EXPERT" &&
                        it.selectedBackend == "LOCAL_OLLAMA" &&
                        it.localHost == "localhost" &&
                        it.localPort == 11434 &&
                        it.localModel == "qwen2.5:3b-instruct" &&
                        it.localProfile == "OPTIMIZED_CHAT" &&
                        it.localTemperature == 0.2 &&
                        it.localTopK == 40 &&
                        it.localStopTokens == "END"
                }
            )
        }
    }

    @Test
    fun `getAiBackendSettings replaces legacy default local model`() = runTest {
        coEvery { dao.getSettings() } returns ChatSettingsEntity(
            id = 1,
            strategyType = "SLIDING_WINDOW",
            windowSize = 10,
            selectedBackend = "LOCAL_OLLAMA",
            localHost = "10.0.2.2",
            localPort = 11434,
            localModel = "llama3.2"
        )

        val settings = repository.getAiBackendSettings()

        assertEquals(AiBackendType.LOCAL_OLLAMA, settings.selectedBackend)
        assertEquals("qwen2.5:3b-instruct", settings.localConfig.model)
    }
}
