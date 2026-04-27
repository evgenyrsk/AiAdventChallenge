package com.example.privateaiservice

import com.example.privateaiservice.api.privateAiModule
import com.example.privateaiservice.config.PrivateAiServiceConfig
import com.example.privateaiservice.service.AuthService
import com.example.privateaiservice.service.HealthService
import com.example.privateaiservice.service.OllamaGatewayResult
import com.example.privateaiservice.service.OllamaGatewayService
import com.example.privateaiservice.service.OllamaHealthResult
import com.example.privateaiservice.service.RateLimitService
import com.example.privateaiservice.service.RequestValidationService
import com.example.privateaiservice.service.UpstreamTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivateAiApplicationTest {

    private val config = PrivateAiServiceConfig(
        privateApiKey = "secret",
        rateLimitRequests = 1,
        rateLimitWindowSeconds = 60,
        maxMessages = 2,
        maxInputChars = 20,
        maxOutputTokens = 50,
        maxContextWindow = 1024
    )

    @Test
    fun `health reports degraded when ollama unavailable`() = testApplication {
        application {
            privateAiModule(
                config = config,
                healthService = HealthService(config, fakeOllama(health = OllamaHealthResult(false, config.defaultModel)))
            )
        }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"status\":\"degraded\""))
    }

    @Test
    fun `chat returns 401 for invalid api key`() = testApplication {
        application {
            privateAiModule(
                config = config,
                authService = AuthService(config.privateApiKey)
            )
        }

        val response = client.post("/v1/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"messages":[{"role":"user","content":"hi"}]}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `chat returns 429 when rate limit exceeded`() = testApplication {
        application {
            privateAiModule(
                config = config,
                rateLimitService = RateLimitService(1, 60_000),
                ollamaGatewayService = fakeOllama()
            )
        }

        repeat(2) { index ->
            val response = client.post("/v1/chat") {
                header(HttpHeaders.Authorization, "Bearer secret")
                contentType(ContentType.Application.Json)
                setBody("""{"messages":[{"role":"user","content":"hi$index"}]}""")
            }
            if (index == 0) {
                assertEquals(HttpStatusCode.OK, response.status)
            } else {
                assertEquals(HttpStatusCode.TooManyRequests, response.status)
            }
        }
    }

    @Test
    fun `chat returns 400 when request exceeds limits`() = testApplication {
        application {
            privateAiModule(
                config = config,
                requestValidationService = RequestValidationService(config)
            )
        }

        val response = client.post("/v1/chat") {
            header(HttpHeaders.Authorization, "Bearer secret")
            contentType(ContentType.Application.Json)
            setBody("""{"messages":[{"role":"user","content":"this request is definitely too large"}]}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `chat returns 504 when ollama times out`() = testApplication {
        application {
            privateAiModule(
                config = config,
                ollamaGatewayService = object : OllamaGatewayService(config) {
                    override fun chat(request: com.example.privateaiservice.api.model.GatewayChatRequest): OllamaGatewayResult {
                        throw UpstreamTimeoutException("Ollama timed out")
                    }
                }
            )
        }

        val response = client.post("/v1/chat") {
            header(HttpHeaders.Authorization, "Bearer secret")
            contentType(ContentType.Application.Json)
            setBody("""{"messages":[{"role":"user","content":"hi"}]}""")
        }

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
    }

    @Test
    fun `chat returns normalized response`() = testApplication {
        application {
            privateAiModule(
                config = config,
                ollamaGatewayService = fakeOllama()
            )
        }

        val response = client.post("/v1/chat") {
            header(HttpHeaders.Authorization, "Bearer secret")
            contentType(ContentType.Application.Json)
            setBody("""{"messages":[{"role":"user","content":"hello"}],"model":"demo"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"model\":\"demo\""))
        assertTrue(body.contains("\"outputChars\":2"))
    }

    private fun fakeOllama(
        health: OllamaHealthResult = OllamaHealthResult(true, "demo")
    ): OllamaGatewayService {
        return object : OllamaGatewayService(config) {
            override fun chat(request: com.example.privateaiservice.api.model.GatewayChatRequest): OllamaGatewayResult {
                return OllamaGatewayResult(
                    message = com.example.privateaiservice.api.model.GatewayChatMessage("assistant", "ok"),
                    model = request.model ?: "demo",
                    latencyMs = 25
                )
            }

            override fun health(): OllamaHealthResult = health
        }
    }
}
