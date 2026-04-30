package com.example.supportassistant.logging

import org.slf4j.LoggerFactory

class SupportRequestLogger {
    private val logger = LoggerFactory.getLogger("SupportAssistant")

    fun request(userId: String?, ticketId: String?) {
        logger.info("support_request_received userId={} ticketId={}", userId ?: "none", ticketId ?: "none")
    }

    fun mcp(tool: String, success: Boolean) {
        logger.info("support_mcp_result tool={} success={}", tool, success)
    }

    fun retrieval(count: Int) {
        logger.info("support_retrieval count={}", count)
    }

    fun llmSuccess(backend: String, latencyMs: Long) {
        logger.info("support_llm_success backend={} latencyMs={}", backend, latencyMs)
    }

    fun failure(errorType: String, message: String) {
        logger.warn("support_failure errorType={} message={}", errorType, message)
    }
}
