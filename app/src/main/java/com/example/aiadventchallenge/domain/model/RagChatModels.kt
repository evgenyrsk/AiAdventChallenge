package com.example.aiadventchallenge.domain.model

import com.example.aiadventchallenge.domain.mcp.RetrievalSummary

enum class ChatFailureCategory {
    RETRIEVAL_UNAVAILABLE,
    RETRIEVAL_EMPTY,
    LOCAL_MODEL_UNAVAILABLE,
    REMOTE_MODEL_UNAVAILABLE,
    TIMEOUT,
    MALFORMED_RESPONSE,
    EMPTY_RESPONSE,
    UNKNOWN
}

data class ChatExecutionInfo(
    val messageId: String? = null,
    val backend: AiBackendType,
    val answerMode: AnswerMode,
    val ragEnabled: Boolean,
    val latencyMs: Long,
    val retrievalLatencyMs: Long? = null,
    val generationLatencyMs: Long? = null,
    val model: String? = null,
    val profile: LocalLlmProfile = LocalLlmProfile.BASELINE,
    val promptProfile: PromptProfile = PromptProfile.BASELINE,
    val responseChars: Int? = null,
    val totalTokens: Int? = null,
    val numCtx: Int? = null,
    val selectedSourceCount: Int = 0,
    val errorCategory: ChatFailureCategory? = null,
    val errorMessage: String? = null
)

data class ChatSourcePreview(
    val title: String,
    val subtitle: String,
    val score: Double? = null
)

data class ChatAnswerPresentation(
    val messageId: String,
    val executionInfo: ChatExecutionInfo,
    val sources: List<ChatSourcePreview> = emptyList(),
    val retrievalSummary: RetrievalSummary? = null
)

data class ModelRunDiagnostics(
    val profile: LocalLlmProfile,
    val promptProfile: PromptProfile,
    val modelLabel: String,
    val answer: String,
    val latencyMs: Long,
    val retrievalLatencyMs: Long? = null,
    val generationLatencyMs: Long? = null,
    val success: Boolean,
    val errorMessage: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val responseChars: Int = answer.length,
    val quality: QualityEvaluation? = null
)

data class RagComparisonResult(
    val question: String,
    val retrievalSummary: RetrievalSummary,
    val baselineRun: ModelRunDiagnostics,
    val optimizedRun: ModelRunDiagnostics,
    val comparedAt: Long = System.currentTimeMillis()
)

data class RagEvaluationSample(
    val label: String,
    val question: String
)

data class RagEvaluationEntry(
    val sample: RagEvaluationSample,
    val comparison: RagComparisonResult
)

data class RagEvaluationRunResult(
    val entries: List<RagEvaluationEntry>,
    val startedAt: Long,
    val finishedAt: Long
) {
    val totalCount: Int
        get() = entries.size

    val successCount: Int
        get() = entries.count { it.comparison.baselineRun.success && it.comparison.optimizedRun.success }

    val averageBaselineLatencyMs: Long
        get() = entries.map { it.comparison.baselineRun.latencyMs }.average().toLong()

    val averageOptimizedLatencyMs: Long
        get() = entries.map { it.comparison.optimizedRun.latencyMs }.average().toLong()
}
