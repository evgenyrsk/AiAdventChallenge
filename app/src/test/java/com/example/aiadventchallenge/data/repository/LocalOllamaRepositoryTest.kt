package com.example.aiadventchallenge.data.repository

import android.util.Log
import com.example.aiadventchallenge.data.api.HttpClient
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import io.mockk.coEvery
import io.mockk.coVerify
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

class LocalOllamaRepositoryTest {

    private val httpClient = mockk<HttpClient>()
    private val aiRequestRepository = mockk<AiRequestRepository>(relaxed = true)
    private val repository = LocalOllamaRepository(httpClient, aiRequestRepository)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        io.mockk.every { Log.d(any(), any()) } returns 0
        io.mockk.every { Log.e(any(), any()) } returns 0
        io.mockk.every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `askWithContext maps ollama response to AnswerWithUsage`() = runTest {
        val requestJsonSlot = slot<String>()
        coEvery {
            httpClient.post(
                url = "http://10.0.2.2:11434/api/chat",
                requestJson = capture(requestJsonSlot),
                headers = any()
            )
        } returns Result.success(
            """
            {
              "message": {
                "role": "assistant",
                "content": "Локальный ответ"
              },
              "prompt_eval_count": 12,
              "eval_count": 8
            }
            """.trimIndent()
        )

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Привет")),
            config = RequestConfig(),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                localConfig = LocalLlmConfig(
                    host = "localhost",
                    port = 11434,
                    model = "qwen2.5:3b-instruct"
                )
            )
        )

        val success = result as ChatResult.Success<AnswerWithUsage>
        assertEquals("Локальный ответ", success.data.content)
        assertEquals(12, success.data.promptTokens)
        assertEquals(8, success.data.completionTokens)
        assertEquals(20, success.data.totalTokens)
        assertTrue(requestJsonSlot.captured.contains(""""stream":false"""))
        coVerify { aiRequestRepository.recordRequest(RequestType.CHAT, "LOCAL_OLLAMA/qwen2.5:3b-instruct", any(), "Локальный ответ", 12, 8, 20) }
    }

    @Test
    fun `normalizeBaseUrl rewrites localhost for emulator`() {
        assertEquals("http://10.0.2.2:11434", repository.normalizeBaseUrl("localhost", 11434))
        assertEquals("http://10.0.2.2:11434", repository.normalizeBaseUrl("127.0.0.1", 11434))
        assertEquals("http://192.168.0.10:11434", repository.normalizeBaseUrl("192.168.0.10", 11434))
    }

    @Test
    fun `askWithContext maps connection error to user friendly message`() = runTest {
        coEvery {
            httpClient.post(
                url = any(),
                requestJson = any(),
                headers = any()
            )
        } returns Result.failure(ConnectException("Connection refused"))

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Привет")),
            config = RequestConfig(),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                localConfig = LocalLlmConfig(model = "qwen2.5:3b-instruct")
            )
        )

        assertTrue(result is ChatResult.Error)
        assertEquals(
            "Не удалось подключиться к локальной модели. Проверьте, что Ollama запущена на Mac и доступна по указанному адресу.",
            (result as ChatResult.Error).message
        )
    }

    @Test
    fun `askWithContext maps model not found to actionable message`() = runTest {
        coEvery {
            httpClient.post(
                url = any(),
                requestJson = any(),
                headers = any()
            )
        } returns Result.failure(
            HttpClient.HttpException(404, """{"error":"model 'llama3.2' not found"}""")
        )

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Привет")),
            config = RequestConfig(),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                localConfig = LocalLlmConfig(model = "llama3.2")
            )
        )

        assertTrue(result is ChatResult.Error)
        assertEquals(
            "Модель 'llama3.2' не найдена в Ollama. Укажите точное имя из 'ollama list', например 'qwen2.5:3b-instruct'.",
            (result as ChatResult.Error).message
        )
    }

    @Test
    fun `askWithContext aggregates ndjson streaming response`() = runTest {
        coEvery {
            httpClient.post(
                url = any(),
                requestJson = any(),
                headers = any()
            )
        } returns Result.success(
            """
            {"model":"qwen2.5:3b-instruct","message":{"role":"assistant","content":"Привет"},"done":false}
            {"model":"qwen2.5:3b-instruct","message":{"role":"assistant","content":" мир"},"done":false}
            {"model":"qwen2.5:3b-instruct","message":{"role":"assistant","content":"!"},"done":true,"done_reason":"stop","prompt_eval_count":30,"eval_count":12}
            """.trimIndent()
        )

        val result = repository.askWithContext(
            messages = listOf(Message(MessageRole.USER, "Привет")),
            config = RequestConfig(),
            requestType = RequestType.CHAT,
            backendSettings = AiBackendSettings(
                localConfig = LocalLlmConfig(model = "qwen2.5:3b-instruct")
            )
        )

        val success = result as ChatResult.Success<AnswerWithUsage>
        assertEquals("Привет мир!", success.data.content)
        assertEquals(30, success.data.promptTokens)
        assertEquals(12, success.data.completionTokens)
        assertEquals(42, success.data.totalTokens)
    }
}
