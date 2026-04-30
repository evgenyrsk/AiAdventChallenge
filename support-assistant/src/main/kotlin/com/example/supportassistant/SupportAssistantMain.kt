package com.example.supportassistant

import com.example.supportassistant.api.supportAssistantModule
import com.example.supportassistant.config.SupportConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = SupportConfig.fromEnv()
    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host
    ) {
        supportAssistantModule(config)
    }.start(wait = true)
}
