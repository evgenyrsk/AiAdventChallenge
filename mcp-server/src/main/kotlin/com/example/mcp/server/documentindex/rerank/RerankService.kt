package com.example.mcp.server.documentindex.rerank

import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy
import com.example.mcp.server.documentindex.model.SearchResultChunk

class RerankService(
    private val client: RerankClient = HttpRerankClient(),
    private val queryComposer: RerankQueryComposer = RerankQueryComposer(),
    private val modelName: String = System.getenv("RERANKER_MODEL") ?: "BAAI/bge-reranker-base",
    private val providerName: String = "self_hosted_http"
) {

    fun rerank(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig,
        heuristicScore: (SearchResultChunk) -> Double
    ): RerankExecutionResult {
        if (candidates.isEmpty()) {
            return RerankExecutionResult(
                strategy = EffectiveRerankStrategy.NONE,
                inputCount = 0,
                outputCount = 0,
                scoreThreshold = config.rerankScoreThreshold,
                timeoutMs = config.rerankTimeoutMs
            )
        }

        val mode = config.postProcessingMode
        if (!config.rerankEnabled || !mode.usesModelRerank()) {
            return RerankExecutionResult(
                strategy = EffectiveRerankStrategy.NONE,
                inputCount = candidates.size,
                outputCount = candidates.size,
                scoreThreshold = config.rerankScoreThreshold,
                timeoutMs = config.rerankTimeoutMs
            )
        }

        val query = queryComposer.compose(
            effectiveQuery = effectiveQuery,
            queryContext = config.queryContext
                ?: buildDefaultQueryContext(originalQuery, rewrittenQuery)
        )

        return runCatching {
            val response = client.rerank(
                RerankRequest(
                    query = query,
                    candidates = candidates.map { it.toRerankCandidate() },
                    topKAfter = config.finalTopK.coerceAtLeast(1),
                    minScoreThreshold = config.rerankScoreThreshold,
                    timeoutMs = config.rerankTimeoutMs,
                    queryContext = config.queryContext
                )
            )
            val ranked = response.results
                .sortedBy { it.rank }
                .take(config.finalTopK.coerceAtLeast(1))
            RerankExecutionResult(
                strategy = EffectiveRerankStrategy.MODEL,
                provider = response.provider.ifBlank { providerName },
                model = response.model.ifBlank { modelName },
                applied = true,
                inputCount = response.debug.inputCandidateCount,
                outputCount = ranked.size,
                scoreThreshold = response.debug.thresholdApplied ?: config.rerankScoreThreshold,
                timeoutMs = config.rerankTimeoutMs,
                fallbackUsed = response.debug.fallbackUsed,
                fallbackReason = response.debug.fallbackReason,
                scoreByChunkId = ranked.associate { it.chunkId to it.rerankScore },
                orderedChunkIds = ranked.map { it.chunkId }
            )
        }.getOrElse { error ->
            when (config.rerankFallbackPolicy) {
                RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL -> {
                    val heuristicScores = candidates.associate { candidate ->
                        candidate.chunkId to heuristicScore(candidate)
                    }
                    val ordered = candidates
                        .sortedByDescending { heuristicScores[it.chunkId] ?: Double.NEGATIVE_INFINITY }
                        .map { it.chunkId }
                    RerankExecutionResult(
                        strategy = EffectiveRerankStrategy.HEURISTIC,
                        provider = providerName,
                        model = modelName,
                        applied = false,
                        inputCount = candidates.size,
                        outputCount = candidates.size.coerceAtMost(config.finalTopK.coerceAtLeast(1)),
                        scoreThreshold = config.rerankScoreThreshold,
                        timeoutMs = config.rerankTimeoutMs,
                        fallbackUsed = true,
                        fallbackReason = "model_rerank_failed:${error.message.orEmpty()}",
                        scoreByChunkId = heuristicScores,
                        orderedChunkIds = ordered
                    )
                }

                RetrievalRerankFallbackPolicy.RETRIEVAL_ONLY -> RerankExecutionResult(
                    strategy = EffectiveRerankStrategy.RETRIEVAL,
                    provider = providerName,
                    model = modelName,
                    applied = false,
                    inputCount = candidates.size,
                    outputCount = candidates.size.coerceAtMost(config.finalTopK.coerceAtLeast(1)),
                    scoreThreshold = config.rerankScoreThreshold,
                    timeoutMs = config.rerankTimeoutMs,
                    fallbackUsed = true,
                    fallbackReason = "model_rerank_failed:${error.message.orEmpty()}",
                    scoreByChunkId = candidates.associate { it.chunkId to it.score },
                    orderedChunkIds = candidates.sortedByDescending { it.score }.map { it.chunkId }
                )
            }
        }
    }

    private fun RetrievalPostProcessingMode.usesModelRerank(): Boolean {
        return this == RetrievalPostProcessingMode.MODEL_RERANK ||
            this == RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK
    }

    private fun buildDefaultQueryContext(originalQuery: String, rewrittenQuery: String?): String? {
        val normalizedRewrite = rewrittenQuery
            ?.takeIf { it.isNotBlank() && !it.equals(originalQuery, ignoreCase = true) }
            ?: return null
        return "Rewritten retrieval query: $normalizedRewrite"
    }
}
