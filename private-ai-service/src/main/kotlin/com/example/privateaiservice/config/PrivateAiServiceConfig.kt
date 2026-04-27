package com.example.privateaiservice.config

data class PrivateAiServiceConfig(
    val host: String = getenv("HOST", "0.0.0.0"),
    val port: Int = getenvInt("PORT", 8085),
    val ollamaBaseUrl: String = getenv("OLLAMA_BASE_URL", "http://localhost:11434"),
    val privateApiKey: String = getenv("PRIVATE_AI_API_KEY", ""),
    val defaultModel: String = getenv("DEFAULT_MODEL", "qwen2.5:3b-instruct"),
    val requestTimeoutMs: Long = getenvLong("REQUEST_TIMEOUT_MS", 120_000L),
    val rateLimitRequests: Int = getenvInt("RATE_LIMIT_REQUESTS", 10),
    val rateLimitWindowSeconds: Long = getenvLong("RATE_LIMIT_WINDOW_SECONDS", 60L),
    val maxMessages: Int = getenvInt("MAX_MESSAGES", 24),
    val maxInputChars: Int = getenvInt("MAX_INPUT_CHARS", 12_000),
    val maxOutputTokens: Int = getenvInt("MAX_OUTPUT_TOKENS", 700),
    val maxContextWindow: Int = getenvInt("MAX_CONTEXT_WINDOW", 4096)
) {
    companion object {
        fun fromEnv(): PrivateAiServiceConfig = PrivateAiServiceConfig()

        private fun getenv(name: String, default: String): String {
            return System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
        }

        private fun getenvInt(name: String, default: Int): Int {
            return System.getenv(name)?.toIntOrNull() ?: default
        }

        private fun getenvLong(name: String, default: Long): Long {
            return System.getenv(name)?.toLongOrNull() ?: default
        }
    }
}
