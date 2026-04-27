package com.example.aiadventchallenge.data.repository

import android.util.Log
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.PrivateAiServiceConfig
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.ConnectException

class PrivateAiServiceRepositoryTest {

    private val httpClient = mockk<HttpClient>()
    private val aiRequestRepository = mockk<AiRequestRepository>(relaxed = true)
    private val repository = PrivateAiServiceRepository(httpClient, aiRequestRepository)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `askWithContext sends bearer auth and maps normalized response`() = runTest {
        val requestJson = slot<String>()
        val headers = slot<Map<String, String>>()
        coEvery {
            httpClient.post(
                url = "http://10.0.2.2:8085/v1/chat",
                requestJson = capture(requestJson),
                headers = capture(headers),
                timeoutMs = 45000L
            )
        } returns Result.success(
            """
            {
              "message":{"role":"assistant","content":"Private answer"},
              "model":"demo-model",
              "usage":{"inputChars":5,"outputChars":14},
              "metrics":{"latencyMs":123}
            }
            """.trimIndent()
        )

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Hello")),
            config = RequestConfig(maxTokens = 256, numCtx = 2048),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                selectedBackend = AiBackendType.PRIVATE_AI_SERVICE,
                privateServiceConfig = PrivateAiServiceConfig(
                    baseUrl = "http://10.0.2.2:8085",
                    apiKey = "secret",
                    model = "demo-model",
                    timeoutMs = 45000L
                )
            )
        )

        val success = result as ChatResult.Success<AnswerWithUsage>
        assertEquals("Private answer", success.data.content)
        assertTrue(requestJson.captured.contains(""""maxTokens":256"""))
        assertTrue(requestJson.captured.contains(""""contextWindow":2048"""))
        assertEquals("Bearer secret", headers.captured["Authorization"])
        coVerify {
            aiRequestRepository.recordRequest(RequestType.CHAT, "PRIVATE_AI_SERVICE/demo-model", any(), "Private answer", null, null, null)
        }
    }

    @Test
    fun `askWithContext maps 401 to friendly message`() = runTest {
        coEvery {
            httpClient.post(url = any(), requestJson = any(), headers = any(), timeoutMs = any())
        } returns Result.failure(HttpClient.HttpException(401, """{"error":"Invalid API key"}"""))

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Hello")),
            config = RequestConfig(),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                selectedBackend = AiBackendType.PRIVATE_AI_SERVICE,
                privateServiceConfig = PrivateAiServiceConfig(apiKey = "bad")
            )
        )

        assertEquals("Неверный API key для private AI service.", (result as ChatResult.Error).message)
    }

    @Test
    fun `askWithContext maps connection error`() = runTest {
        coEvery {
            httpClient.post(url = any(), requestJson = any(), headers = any(), timeoutMs = any())
        } returns Result.failure(ConnectException("Connection refused"))

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Hello")),
            config = RequestConfig(),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                selectedBackend = AiBackendType.PRIVATE_AI_SERVICE,
                privateServiceConfig = PrivateAiServiceConfig()
            )
        )

        assertEquals(
            "Private AI service недоступен. Проверьте, что gateway запущен и доступен по указанному адресу.",
            (result as ChatResult.Error).message
        )
    }
}
