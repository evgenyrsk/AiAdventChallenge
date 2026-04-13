package com.example.mcp.server.servers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.example.mcp.server.handler.AbstractMcpJsonRpcHandler
import kotlinx.coroutines.*
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress

abstract class McpServer(
    protected val port: Int,
    protected val serverName: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    protected abstract val handler: AbstractMcpJsonRpcHandler
    
    private var httpServer: HttpServer? = null
    private var serverJob: Job? = null
    
    suspend fun start() {
        println("🚀 Starting $serverName on http://10.0.2.2:$port")
        
        serverJob = scope.launch {
            try {
                httpServer = HttpServer.create(InetSocketAddress(port), 0)
                httpServer?.createContext("/") { exchange ->
                    handleExchange(exchange)
                }
                httpServer?.start()
                
                println("✅ $serverName is running on port $port")
                println("📡 Android Emulator should use: http://10.0.2.2:$port")
                println("🖥️  For testing from host: http://localhost:$port")
                
                while (isActive) {
                    delay(1000)
                }
            } catch (e: Exception) {
                println("❌ $serverName error: ${e.message}")
            }
        }
    }
    
    private fun handleExchange(exchange: HttpExchange) {
        try {
            val requestBody = exchange.requestBody.bufferedReader().use { it.readText() }
            
            println("📨 [$serverName] Request: $requestBody")
            
            val response = handler.handle(requestBody)
            
            exchange.sendResponseHeaders(200, 0)
            val outputStream = exchange.responseBody
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(response)
            }
            outputStream.close()
            
            println("📤 [$serverName] Response: ${response.take(100)}...")
        } catch (e: Exception) {
            println("❌ [$serverName] Error: ${e.message}")
            e.printStackTrace()
            exchange.sendResponseHeaders(500, 0)
            exchange.responseBody.close()
        }
    }
    
    suspend fun stop() {
        serverJob?.cancel()
        httpServer?.stop(0)
        println("🛑 $serverName stopped")
    }
}
