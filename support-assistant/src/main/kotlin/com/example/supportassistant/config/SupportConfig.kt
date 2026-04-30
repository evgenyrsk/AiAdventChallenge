package com.example.supportassistant.config

data class SupportConfig(
    val host: String = env("SUPPORT_HOST", "0.0.0.0"),
    val port: Int = envInt("SUPPORT_PORT", 8091),
    val dataDirectory: String = resolvePath(
        envName = "SUPPORT_DATA_DIR",
        candidates = listOf("support-assistant/data", "data")
    ),
    val promptPath: String = resolvePath(
        envName = "SUPPORT_PROMPT_PATH",
        candidates = listOf(
            "support-assistant/prompts/support_assistant_prompt.md",
            "prompts/support_assistant_prompt.md"
        )
    ),
    val ragTopK: Int = envInt("SUPPORT_RAG_TOP_K", 4),
    val ragMaxContextChars: Int = envInt("SUPPORT_RAG_MAX_CONTEXT_CHARS", 4000),
    val llmBackend: String = env("SUPPORT_LLM_BACKEND", "private"),
    val llmBaseUrl: String = env("SUPPORT_LLM_BASE_URL", "http://localhost:8085"),
    val llmApiKey: String = env("SUPPORT_LLM_API_KEY", ""),
    val llmModel: String = env("SUPPORT_LLM_MODEL", "qwen2.5:3b-instruct"),
    val llmTimeoutMs: Long = envLong("SUPPORT_LLM_TIMEOUT_MS", 120_000L)
) {
    companion object {
        fun fromEnv(): SupportConfig = SupportConfig()

        private fun env(name: String, default: String): String {
            return System.getenv(name)?.takeIf { it.isNotBlank() } ?: default
        }

        private fun envInt(name: String, default: Int): Int {
            return System.getenv(name)?.toIntOrNull() ?: default
        }

        private fun envLong(name: String, default: Long): Long {
            return System.getenv(name)?.toLongOrNull() ?: default
        }

        private fun resolvePath(envName: String, candidates: List<String>): String {
            System.getenv(envName)?.takeIf { it.isNotBlank() }?.let { return it }
            return candidates.firstOrNull { java.io.File(it).exists() } ?: candidates.first()
        }
    }
}
