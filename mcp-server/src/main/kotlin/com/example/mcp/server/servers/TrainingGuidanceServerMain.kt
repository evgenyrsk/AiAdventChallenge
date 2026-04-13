package com.example.mcp.server.servers

import kotlinx.coroutines.*

suspend fun main() {
    val server = TrainingGuidanceServer(port = 8083)
    server.start()
    
    while (true) {
        delay(1000)
    }
}
