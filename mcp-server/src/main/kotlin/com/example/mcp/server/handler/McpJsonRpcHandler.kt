package com.example.mcp.server.handler

import com.example.mcp.server.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class McpJsonRpcHandler {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val tools = listOf(
        Tool(
            name = "ping",
            description = "Simple ping tool to test MCP connection. Returns 'pong' message."
        ),
        Tool(
            name = "get_app_info",
            description = "Returns information about the application including version, platform, and build details."
        )
    )

    fun handle(requestBody: String): String {
        return try {
            val request = json.decodeFromString<JsonRpcRequest>(requestBody)

            when (request.method) {
                "initialize" -> handleInitialize(request)
                "tools/list" -> handleListTools(request)
                "ping" -> handlePing(request)
                "get_app_info" -> handleGetAppInfo(request)
                else -> handleUnknownMethod(request)
            }
        } catch (e: Exception) {
            val errorResponse = JsonRpcResponse(
                jsonrpc = "2.0",
                id = -1,
                result = null,
                error = JsonRpcError(
                    code = -32600,
                    message = "Invalid Request: ${e.message}"
                )
            )
            json.encodeToString(errorResponse)
        }
    }

    private fun handleInitialize(request: JsonRpcRequest): String {
        println("   Method: initialize")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = "MCP Server initialized successfully"
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleListTools(request: JsonRpcRequest): String {
        println("   Method: tools/list")
        println("   Returning ${tools.size} tools")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                tools = tools
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handlePing(request: JsonRpcRequest): String {
        println("   Method: ping")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = "pong"
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleGetAppInfo(request: JsonRpcRequest): String {
        println("   Method: get_app_info")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = JsonRpcResult(
                message = """
                    App Info:
                    - Name: AiAdventChallenge MCP Test Server
                    - Version: 1.0.0
                    - Platform: JVM
                    - Status: Running
                """.trimIndent()
            ),
            error = null
        )

        return json.encodeToString(response)
    }

    private fun handleUnknownMethod(request: JsonRpcRequest): String {
        println("   Method: ${request.method} (unknown)")

        val response = JsonRpcResponse(
            jsonrpc = "2.0",
            id = request.id,
            result = null,
            error = JsonRpcError(
                code = -32601,
                message = "Method not found: ${request.method}"
            )
        )

        return json.encodeToString(response)
    }
}
