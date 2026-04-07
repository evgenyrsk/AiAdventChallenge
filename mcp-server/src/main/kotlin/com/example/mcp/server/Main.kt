package com.example.mcp.server

import com.example.mcp.server.handler.McpJsonRpcHandler
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress

suspend fun main() {
    println("🚀 Starting MCP Server on http://10.0.2.2:8080")

    val handler = McpJsonRpcHandler()

    withContext(Dispatchers.IO) {
        while (true) {
            try {
                val server = com.sun.net.httpserver.HttpServer.create(InetSocketAddress(8080), 0)
                server.createContext("/") { exchange ->
                    try {
                        val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }

                        println("📨 Request: $requestBody")

                        val response = handler.handle(requestBody)

                        exchange.sendResponseHeaders(200, 0)
                        val outputStream = exchange.responseBody
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(response)
                        }
                        outputStream.close()

                        println("📤 Response: $response")
                    } catch (e: Exception) {
                        e.printStackTrace()
                        exchange.sendResponseHeaders(500, 0)
                        exchange.responseBody.close()
                    }
                }
                server.start()

                println("✅ MCP Server is running on http://10.0.2.2:8080")
                println("📡 Android Emulator should use: http://10.0.2.2:8080")
                println("🖥️  For testing from host: http://localhost:8080")

                while (true) {
                    delay(1000)
                }
            } catch (e: Exception) {
                println("❌ Server error: ${e.message}")
                delay(5000)
            }
        }
    }
}
