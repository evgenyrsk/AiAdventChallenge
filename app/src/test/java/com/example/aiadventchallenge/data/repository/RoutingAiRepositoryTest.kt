package com.example.aiadventchallenge.data.repository

import android.util.Log
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoutingAiRepositoryTest {

    private val chatSettingsRepository = mockk<ChatSettingsRepository>()
    private val remoteRepository = mockk<AiRepository>()
    private val localOllamaRepository = mockk<LocalOllamaRepository>()
    private val routingRepository = RoutingAiRepository(
        chatSettingsRepository = chatSettingsRepository,
        remoteRepository = remoteRepository,
        localOllamaRepository = localOllamaRepository
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        io.mockk.every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `askWithContext routes to remote backend`() = runTest {
        val messages = listOf(Message(MessageRole.USER, "Привет"))
        val expected = ChatResult.Success(
            AnswerWithUsage(
                content = "Remote",
                promptTokens = null,
                completionTokens = null,
                totalTokens = null
            )
        )
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.REMOTE
        )
        coEvery { remoteRepository.askWithContext(messages, any(), RequestType.CHAT) } returns expected

        val result = routingRepository.askWithContext(messages, RequestConfig(), RequestType.CHAT)

        assertEquals(expected, result)
        coVerify(exactly = 1) { remoteRepository.askWithContext(messages, any(), RequestType.CHAT) }
        coVerify(exactly = 0) { localOllamaRepository.askWithContext(any(), any(), any(), any()) }
    }

    @Test
    fun `askWithContext routes to local backend`() = runTest {
        val messages = listOf(Message(MessageRole.USER, "Привет"))
        val expected = ChatResult.Success(
            AnswerWithUsage(
                content = "Local",
                promptTokens = null,
                completionTokens = null,
                totalTokens = null
            )
        )
        val settings = AiBackendSettings(selectedBackend = AiBackendType.LOCAL_OLLAMA)
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns settings
        coEvery { localOllamaRepository.askWithContext(messages, any(), RequestType.CHAT, settings) } returns expected

        val result = routingRepository.askWithContext(messages, RequestConfig(), RequestType.CHAT)

        assertEquals(expected, result)
        coVerify(exactly = 0) { remoteRepository.askWithContext(any<List<Message>>(), any(), RequestType.CHAT) }
        coVerify(exactly = 1) { localOllamaRepository.askWithContext(messages, any(), RequestType.CHAT, settings) }
    }
}
