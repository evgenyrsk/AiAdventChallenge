package com.example.supportassistant

import com.example.supportassistant.api.supportAssistantModule
import com.example.supportassistant.config.SupportConfig
import com.example.supportassistant.llm.LlmResult
import com.example.supportassistant.llm.SupportLlmClient
import com.example.supportassistant.mcp.JsonMockCrmMcp
import com.example.supportassistant.prompt.SupportPromptAssembler
import com.example.supportassistant.rag.SupportRagRetriever
import com.example.supportassistant.service.SupportAssistantUseCase
import com.example.supportassistant.service.SupportLlmUnavailableException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportAssistantApplicationTest {
    private val config = SupportConfig(
        dataDirectory = "data",
        promptPath = "prompts/support_assistant_prompt.md",
        llmBackend = "private"
    )

    @Test
    fun `health returns ok when support FAQ exists`() = testApplication {
        application {
            supportAssistantModule(config = config, useCase = useCase(fakeLlm()))
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"ok\""))
    }

    @Test
    fun `support ask uses ticket and user context`() = testApplication {
        application {
            supportAssistantModule(config = config, useCase = useCase(fakeLlm()))
        }

        val response = client.post("/support/ask") {
            contentType(ContentType.Application.Json)
            setBody("""{"question":"Почему не работает авторизация?","userId":"user_001","ticketId":"ticket_123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"ticketContextUsed\":true"))
        assertTrue(body.contains("\"userContextUsed\":true"))
        assertTrue(body.contains("\"sources\""))
        assertTrue(body.contains("Авторизация"))
    }

    @Test
    fun `support ask without ticket still uses user context`() = testApplication {
        application {
            supportAssistantModule(config = config, useCase = useCase(fakeLlm()))
        }

        val response = client.post("/support/ask") {
            contentType(ContentType.Application.Json)
            setBody("""{"question":"Почему пропала история сообщений?","userId":"user_002"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"ticketContextUsed\":false"))
        assertTrue(body.contains("\"userContextUsed\":true"))
    }

    @Test
    fun `unknown user does not crash`() = testApplication {
        application {
            supportAssistantModule(config = config, useCase = useCase(fakeLlm()))
        }

        val response = client.post("/support/ask") {
            contentType(ContentType.Application.Json)
            setBody("""{"question":"Почему не работает авторизация?","userId":"missing_user","ticketId":"ticket_123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"userContextUsed\":false"))
        assertTrue(body.contains("Проверить корректность userId"))
    }

    @Test
    fun `llm unavailable returns degraded structured response`() = testApplication {
        application {
            supportAssistantModule(config = config, useCase = useCase(unavailableLlm()))
        }

        val response = client.post("/support/ask") {
            contentType(ContentType.Application.Json)
            setBody("""{"question":"Почему локальная модель не отвечает?","userId":"user_001","ticketId":"ticket_126"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"confidence\":\"low\""))
        assertTrue(body.contains("LLM backend"))
        assertTrue(body.contains("Локальная LLM"))
    }

    @Test
    fun `blank question returns bad request`() = testApplication {
        application {
            supportAssistantModule(config = config, useCase = useCase(fakeLlm()))
        }

        val response = client.post("/support/ask") {
            contentType(ContentType.Application.Json)
            setBody("""{"question":"   ","userId":"user_001"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun useCase(llm: SupportLlmClient): SupportAssistantUseCase {
        return SupportAssistantUseCase(
            config = config,
            mcp = JsonMockCrmMcp(config.dataDirectory),
            retriever = SupportRagRetriever(config.dataDirectory, config.ragTopK, config.ragMaxContextChars),
            promptAssembler = SupportPromptAssembler(config.promptPath),
            llmClient = llm
        )
    }

    private fun fakeLlm(): SupportLlmClient {
        return object : SupportLlmClient {
            override suspend fun generate(prompt: String): LlmResult {
                return LlmResult(
                    content = """
                        Краткий ответ
                        Нужно проверить данные тикета и выполнить шаги из FAQ.

                        Вероятная причина
                        Авторизация может зависать из-за таймаута Google OAuth.

                        Что сделать пользователю
                        - Повторить вход.

                        Что проверить поддержке
                        - Проверить errorCode.

                        Источники
                        - FAQ

                        Уверенность: medium
                    """.trimIndent(),
                    latencyMs = 10
                )
            }
        }
    }

    private fun unavailableLlm(): SupportLlmClient {
        return object : SupportLlmClient {
            override suspend fun generate(prompt: String): LlmResult {
                throw SupportLlmUnavailableException("timeout")
            }
        }
    }
}
