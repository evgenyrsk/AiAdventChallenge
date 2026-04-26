package com.example.privateaiservice

import com.example.privateaiservice.api.privateAiModule
import com.example.privateaiservice.config.PrivateAiServiceConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val config = PrivateAiServiceConfig.fromEnv()
    embeddedServer(
        factory = Netty,
        port = config.port,
        host = config.host
    ) {
        privateAiModule(config)
    }.start(wait = true)
}
