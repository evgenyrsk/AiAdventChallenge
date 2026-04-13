package com.example.mcp.server.servers

import kotlinx.coroutines.*

suspend fun main() {
    val server = NutritionMetricsServer(port = 8081)
    server.start()
    
    while (true) {
        delay(1000)
    }
}
