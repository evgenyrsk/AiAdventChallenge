package com.example.privateaiservice.api

import com.example.privateaiservice.api.model.GatewayChatRequest
import com.example.privateaiservice.api.model.GatewayChatResponse
import com.example.privateaiservice.api.model.GatewayErrorResponse
import com.example.privateaiservice.api.model.GatewayUsage
import com.example.privateaiservice.api.model.GatewayMetrics
import com.example.privateaiservice.config.PrivateAiServiceConfig
import com.example.privateaiservice.logging.RequestLogger
import com.example.privateaiservice.service.AuthService
import com.example.privateaiservice.service.HealthService
import com.example.privateaiservice.service.OllamaGatewayService
import com.example.privateaiservice.service.RateLimitException
import com.example.privateaiservice.service.RateLimitService
import com.example.privateaiservice.service.RequestValidationService
import com.example.privateaiservice.service.ServiceException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun Application.privateAiModule(
    config: PrivateAiServiceConfig = PrivateAiServiceConfig.fromEnv(),
    authService: AuthService = AuthService(config.privateApiKey),
    rateLimitService: RateLimitService = RateLimitService(
        maxRequests = config.rateLimitRequests,
        windowMs = config.rateLimitWindowSeconds * 1000
    ),
    requestValidationService: RequestValidationService = RequestValidationService(config),
    ollamaGatewayService: OllamaGatewayService = OllamaGatewayService(config),
    healthService: HealthService = HealthService(config, ollamaGatewayService),
    requestLogger: RequestLogger = RequestLogger()
) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    install(CallLogging)
    install(ContentNegotiation) {
        json(json)
    }
    install(StatusPages) {
        exception<ServiceException> { call, cause ->
            val status = HttpStatusCode.fromValue(cause.statusCode)
            call.respond(status, GatewayErrorResponse(error = cause.message, code = cause.statusCode))
        }
        exception<Throwable> { call, cause ->
            requestLogger.failure("unknown", cause::class.simpleName ?: "Unexpected", cause.message ?: "Unexpected error")
            call.respond(
                HttpStatusCode.InternalServerError,
                GatewayErrorResponse(error = "Unexpected server error", code = 500)
            )
        }
    }

    routing {
        get("/health") {
            call.respond(healthService.getHealth())
        }

        post("/v1/chat") {
            val clientAddress = call.request.header("X-Forwarded-For")
                ?: call.request.local.remoteHost
                ?: "unknown"
            val authHeader = call.request.header("Authorization")
            requestLogger.authResult(success = authHeader != null, client = clientAddress)
            authService.authenticate(authHeader)
            try {
                rateLimitService.check(authHeader ?: clientAddress)
            } catch (rateLimit: RateLimitException) {
                requestLogger.rateLimited(clientAddress)
                throw rateLimit
            }

            val payload = call.receive<GatewayChatRequest>()
            requestLogger.request("/v1/chat", payload.model, clientAddress)
            requestValidationService.validate(payload)

            val inputChars = payload.messages.sumOf { it.content.length }
            val result = ollamaGatewayService.chat(payload)
            requestLogger.success("/v1/chat", result.model, result.latencyMs, result.message.content.length)

            call.respond(
                HttpStatusCode.OK,
                GatewayChatResponse(
                    message = result.message,
                    model = result.model,
                    usage = GatewayUsage(
                        inputChars = inputChars,
                        outputChars = result.message.content.length
                    ),
                    metrics = GatewayMetrics(latencyMs = result.latencyMs)
                )
            )
        }
    }
}
