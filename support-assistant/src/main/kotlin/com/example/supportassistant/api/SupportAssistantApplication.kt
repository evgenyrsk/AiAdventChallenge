package com.example.supportassistant.api

import com.example.supportassistant.config.SupportConfig
import com.example.supportassistant.llm.HttpSupportLlmClient
import com.example.supportassistant.logging.SupportRequestLogger
import com.example.supportassistant.mcp.JsonMockCrmMcp
import com.example.supportassistant.model.HealthResponse
import com.example.supportassistant.model.SupportAskRequest
import com.example.supportassistant.model.SupportErrorResponse
import com.example.supportassistant.prompt.SupportPromptAssembler
import com.example.supportassistant.rag.SupportRagRetriever
import com.example.supportassistant.service.InvalidSupportRequestException
import com.example.supportassistant.service.SupportAssistantUseCase
import com.example.supportassistant.service.SupportDataUnavailableException
import com.example.supportassistant.service.SupportException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

fun Application.supportAssistantModule(
    config: SupportConfig = SupportConfig.fromEnv(),
    mcp: JsonMockCrmMcp = JsonMockCrmMcp(config.dataDirectory),
    retriever: SupportRagRetriever = SupportRagRetriever(config.dataDirectory, config.ragTopK, config.ragMaxContextChars),
    useCase: SupportAssistantUseCase = SupportAssistantUseCase(
        config = config,
        mcp = mcp,
        retriever = retriever,
        promptAssembler = SupportPromptAssembler(config.promptPath),
        llmClient = HttpSupportLlmClient(config)
    ),
    logger: SupportRequestLogger = SupportRequestLogger()
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
        exception<SupportException> { call, cause ->
            logger.failure(cause::class.simpleName ?: "SupportException", cause.message)
            call.respond(
                HttpStatusCode.fromValue(cause.statusCode),
                SupportErrorResponse(error = cause.message, code = cause.statusCode)
            )
        }
        exception<Throwable> { call, cause ->
            logger.failure(cause::class.simpleName ?: "Unexpected", cause.message ?: "Unexpected error")
            call.respond(
                HttpStatusCode.InternalServerError,
                SupportErrorResponse(error = "Unexpected support assistant error", code = 500)
            )
        }
    }

    routing {
        get("/health") {
            val ragReady = retriever.isReady()
            call.respond(
                HealthResponse(
                    status = if (ragReady) "ok" else "degraded",
                    llmBackend = config.llmBackend,
                    ragReady = ragReady
                )
            )
        }

        post("/support/ask") {
            val payload = call.receive<SupportAskRequest>()
            call.respond(HttpStatusCode.OK, useCase.ask(payload))
        }

        get("/support/users/{id}") {
            val userId = call.parameters["id"] ?: throw InvalidSupportRequestException("Missing user id")
            val result = mcp.getUserById(userId)
            if (result.ok && result.data != null) {
                call.respond(result.data)
            } else {
                call.respond(HttpStatusCode.NotFound, SupportErrorResponse(result.error ?: "User not found", 404))
            }
        }

        get("/support/tickets/{id}") {
            val ticketId = call.parameters["id"] ?: throw InvalidSupportRequestException("Missing ticket id")
            val result = mcp.getTicketById(ticketId)
            if (result.ok && result.data != null) {
                call.respond(result.data)
            } else {
                call.respond(HttpStatusCode.NotFound, SupportErrorResponse(result.error ?: "Ticket not found", 404))
            }
        }
    }
}
