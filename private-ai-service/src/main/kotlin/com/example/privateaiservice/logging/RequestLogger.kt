package com.example.privateaiservice.logging

import org.slf4j.LoggerFactory

class RequestLogger {
    private val logger = LoggerFactory.getLogger("PrivateAiService")

    fun request(endpoint: String, model: String?, client: String) {
        logger.info("request_received endpoint={} model={} client={}", endpoint, model ?: "default", client)
    }

    fun authResult(success: Boolean, client: String) {
        logger.info("auth client={} success={}", client, success)
    }

    fun rateLimited(client: String) {
        logger.warn("rate_limit client={}", client)
    }

    fun success(endpoint: String, model: String, latencyMs: Long, outputChars: Int) {
        logger.info("request_success endpoint={} model={} latencyMs={} outputChars={}", endpoint, model, latencyMs, outputChars)
    }

    fun failure(endpoint: String, errorType: String, message: String) {
        logger.warn("request_failure endpoint={} errorType={} message={}", endpoint, errorType, message)
    }
}
