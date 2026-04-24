package com.example.aiadventchallenge.domain.model

enum class LocalLlmProfile {
    BASELINE,
    OPTIMIZED_CHAT,
    OPTIMIZED_RAG
}

enum class PromptProfile {
    BASELINE,
    OPTIMIZED_CHAT,
    OPTIMIZED_RAG
}

data class LocalLlmRuntimeOptions(
    val temperature: Double? = null,
    val numPredict: Int? = null,
    val numCtx: Int? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val repeatPenalty: Double? = null,
    val seed: Int? = null,
    val stop: List<String>? = null,
    val keepAlive: String? = null
)

data class LocalLlmExecutionSettings(
    val profile: LocalLlmProfile,
    val promptProfile: PromptProfile,
    val runtimeOptions: LocalLlmRuntimeOptions
)

data class QualityEvaluation(
    val relevance: String,
    val groundedness: String,
    val clarity: String,
    val conciseness: String,
    val hallucinationRisk: String,
    val summary: String
)

data class OptimizationRunMetrics(
    val profile: LocalLlmProfile,
    val promptProfile: PromptProfile,
    val model: String,
    val totalLatencyMs: Long,
    val retrievalLatencyMs: Long? = null,
    val generationLatencyMs: Long? = null,
    val responseChars: Int = 0,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val numCtx: Int? = null,
    val success: Boolean,
    val errorMessage: String? = null
)
