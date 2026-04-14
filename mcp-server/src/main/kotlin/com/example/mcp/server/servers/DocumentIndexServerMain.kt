package com.example.mcp.server.servers

import kotlinx.coroutines.delay

suspend fun main() {
    val server = DocumentIndexServer(port = 8084)
    server.start()

    while (true) {
        delay(1000)
    }
}
