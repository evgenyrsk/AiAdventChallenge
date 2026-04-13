package com.example.mcp.server.servers

import kotlinx.coroutines.*

suspend fun main() {
    val server = MealGuidanceServer(port = 8082)
    server.start()
    
    while (true) {
        delay(1000)
    }
}
