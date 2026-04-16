package com.example.mcp.server.documentindex.rerank

import com.example.mcp.server.documentindex.model.SearchResultChunk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RerankCandidate(
    val chunkId: String,
    val text: String,
    val title: String,
    val relativePath: String,
    val section: String,
    val retrievalScore: Double,
    val semanticScore: Double,
    val keywordScore: Double
)

@Serializable
data class RerankRequest(
    val query: String,
    val candidates: List<RerankCandidate>,
    val topKAfter: Int,
    val minScoreThreshold: Double? = null,
    val timeoutMs: Long? = null,
    val queryContext: String? = null
)

@Serializable
data class RerankedCandidate(
    val chunkId: String,
    val rerankScore: Double,
    val rank: Int,
    val title: String,
    val relativePath: String,
    val section: String,
    val retrievalScore: Double,
    val semanticScore: Double,
    val keywordScore: Double,
    val filteredOut: Boolean = false,
    val filterReason: String? = null
)

@Serializable
data class RerankDebugInfo(
    val inputCandidateCount: Int,
    val outputCandidateCount: Int,
    val topKAfter: Int,
    val thresholdApplied: Double? = null,
    val timedOut: Boolean = false,
    val fallbackUsed: Boolean = false,
    val fallbackReason: String? = null
)

@Serializable
data class RerankResponse(
    val provider: String = "local_http",
    val model: String,
    val results: List<RerankedCandidate>,
    val debug: RerankDebugInfo
)

enum class EffectiveRerankStrategy {
    NONE,
    RETRIEVAL,
    HEURISTIC,
    MODEL
}

data class RerankExecutionResult(
    val strategy: EffectiveRerankStrategy,
    val provider: String? = null,
    val model: String? = null,
    val applied: Boolean = false,
    val inputCount: Int = 0,
    val outputCount: Int = 0,
    val scoreThreshold: Double? = null,
    val timeoutMs: Long? = null,
    val fallbackUsed: Boolean = false,
    val fallbackReason: String? = null,
    val scoreByChunkId: Map<String, Double> = emptyMap(),
    val orderedChunkIds: List<String> = emptyList()
)

interface RerankClient {
    fun rerank(request: RerankRequest): RerankResponse
}

internal fun SearchResultChunk.toRerankCandidate(): RerankCandidate {
    return RerankCandidate(
        chunkId = chunkId,
        text = text,
        title = title,
        relativePath = relativePath,
        section = section,
        retrievalScore = score,
        semanticScore = semanticScore,
        keywordScore = keywordScore
    )
}

@Serializable
internal data class HttpRerankServiceRequest(
    val query: String,
    val candidates: List<RerankCandidate>,
    @SerialName("top_k_after")
    val topKAfter: Int,
    @SerialName("min_score_threshold")
    val minScoreThreshold: Double? = null,
    @SerialName("timeout_ms")
    val timeoutMs: Long? = null,
    @SerialName("query_context")
    val queryContext: String? = null
)
