package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ChatSettingsDao
import com.example.aiadventchallenge.data.local.entity.ChatSettingsEntity
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.ChatSettingsPayload
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
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
            localModel = "qwen2.5"
        )

        val settings = repository.getAiBackendSettings()

        assertEquals(AiBackendType.LOCAL_OLLAMA, settings.selectedBackend)
        assertEquals("localhost", settings.localConfig.host)
        assertEquals(11434, settings.localConfig.port)
        assertEquals("qwen2.5", settings.localConfig.model)
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
                    model = "qwen2.5:3b-instruct"
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
                        it.localModel == "qwen2.5:3b-instruct"
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
                        model = "qwen2.5:3b-instruct"
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
                        it.localModel == "qwen2.5:3b-instruct"
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
