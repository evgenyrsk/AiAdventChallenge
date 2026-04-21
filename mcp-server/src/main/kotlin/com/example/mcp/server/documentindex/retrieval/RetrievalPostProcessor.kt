package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy
import com.example.mcp.server.documentindex.model.RetrievedContextChunk
import com.example.mcp.server.documentindex.model.SearchResultChunk

interface RetrievalPostProcessor {
    fun process(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig
    ): PostProcessingResult
}

class DefaultRetrievalPostProcessor(
    private val heuristicReranker: Reranker = HeuristicReranker(),
    private val modelReranker: Reranker = ModelReranker()
) : RetrievalPostProcessor {

    override fun process(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig
    ): PostProcessingResult {
        val mode = if (config.postProcessingEnabled) config.postProcessingMode else RetrievalPostProcessingMode.NONE
        val rerankResult = when (mode) {
            RetrievalPostProcessingMode.MODEL_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK -> {
                val primary = modelReranker.rerank(originalQuery, rewrittenQuery, effectiveQuery, candidates, config)
                if (primary.applied) {
                    primary
                } else if (config.rerankFallbackPolicy == RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL) {
                    heuristicReranker.rerank(originalQuery, rewrittenQuery, effectiveQuery, candidates, config).copy(
                        fallbackUsed = true,
                        fallbackReason = primary.fallbackReason ?: "model_rerank_unavailable"
                    )
                } else {
                    primary
                }
            }

            RetrievalPostProcessingMode.HEURISTIC_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK -> heuristicReranker.rerank(
                originalQuery,
                rewrittenQuery,
                effectiveQuery,
                candidates,
                config
            )

            RetrievalPostProcessingMode.NONE,
            RetrievalPostProcessingMode.THRESHOLD_ONLY -> RerankResult(emptyMap(), applied = false)
        }

        val rerankInfo = RerankExecutionInfo(
            provider = when {
                rerankResult.applied && mode in modelModes -> modelReranker.provider
                rerankResult.applied -> heuristicReranker.provider
                else -> null
            },
            model = when {
                rerankResult.applied && mode in modelModes -> modelReranker.model
                else -> null
            },
            applied = rerankResult.applied,
            inputCount = candidates.size,
            outputCount = candidates.size.coerceAtMost(config.finalTopK.coerceAtLeast(1)),
            scoreThreshold = config.rerankScoreThreshold ?: config.similarityThreshold,
            timeoutMs = config.rerankTimeoutMs,
            fallbackUsed = rerankResult.fallbackUsed,
            fallbackReason = rerankResult.fallbackReason
        )

        val scored = candidates.map { candidate ->
            val rerankScore = rerankResult.scoresByChunkId[candidate.chunkId]
                ?: heuristicRerankScore(candidate, originalQuery, rewrittenQuery, effectiveQuery)
            ProcessedCandidate(
                chunk = candidate,
                rerankScore = rerankScore,
                explanation = buildExplanation(candidate, rerankScore)
            )
        }

        val thresholded = scored.filter { candidate -> thresholdReason(candidate, mode, config) == null }
        val ranked = when (mode) {
            RetrievalPostProcessingMode.HEURISTIC_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK,
            RetrievalPostProcessingMode.MODEL_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK -> thresholded.sortedByDescending { it.rerankScore }
            RetrievalPostProcessingMode.NONE,
            RetrievalPostProcessingMode.THRESHOLD_ONLY -> thresholded.sortedByDescending { it.chunk.fusionScore }
        }

        val selected = ranked.take(config.finalTopK.coerceAtLeast(1))
        val selectedIds = selected.map { it.chunk.chunkId }.toSet()
        val filtered = scored.mapNotNull { candidate ->
            when {
                selectedIds.contains(candidate.chunk.chunkId) -> null
                thresholdReason(candidate, mode, config) != null -> candidate.toRetrievedChunk(
                    finalRank = null,
                    filteredOut = true,
                    filterReason = thresholdReason(candidate, mode, config),
                    explanation = candidate.explanation
                )

                else -> candidate.toRetrievedChunk(
                    finalRank = null,
                    filteredOut = true,
                    filterReason = "removed_by_final_topk",
                    explanation = candidate.explanation
                )
            }
        }

        if (selected.isNotEmpty()) {
            return PostProcessingResult(
                finalCandidates = selected.mapIndexed { index, candidate ->
                    candidate.toRetrievedChunk(
                        finalRank = index + 1,
                        filteredOut = false,
                        filterReason = null,
                        explanation = candidate.explanation
                    )
                },
                filteredCandidates = filtered,
                fallbackApplied = false,
                fallbackReason = null,
                rerankExecution = rerankInfo
            )
        }

        if (!config.fallbackOnEmptyPostProcessing || candidates.isEmpty()) {
            return PostProcessingResult(
                finalCandidates = emptyList(),
                filteredCandidates = filtered,
                fallbackApplied = false,
                fallbackReason = null,
                rerankExecution = rerankInfo
            )
        }

        val fallback = candidates
            .sortedByDescending { rerankResult.scoresByChunkId[it.chunkId] ?: it.fusionScore }
            .take(config.finalTopK.coerceAtLeast(1))
            .mapIndexed { index, candidate ->
                candidate.toRetrievedChunk(
                    finalRank = index + 1,
                    filteredOut = false,
                    filterReason = "fallback_restored",
                    explanation = "Restored from initial candidates because post-processing removed every result."
                )
            }

        return PostProcessingResult(
            finalCandidates = fallback,
            filteredCandidates = filtered,
            fallbackApplied = true,
            fallbackReason = "post_processing_removed_all_candidates",
            rerankExecution = rerankInfo.copy(
                fallbackUsed = true,
                fallbackReason = rerankInfo.fallbackReason ?: "post_processing_removed_all_candidates"
            )
        )
    }

    private fun thresholdReason(
        candidate: ProcessedCandidate,
        mode: RetrievalPostProcessingMode,
        config: RetrievalPipelineConfig
    ): String? {
        return when (mode) {
            RetrievalPostProcessingMode.THRESHOLD_ONLY -> {
                val threshold = config.similarityThreshold ?: return null
                if (candidate.chunk.vectorScore < threshold) "below_similarity_threshold" else null
            }

            RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK -> {
                val threshold = config.rerankScoreThreshold ?: config.similarityThreshold ?: return null
                if (candidate.rerankScore < threshold) "below_rerank_threshold" else null
            }

            RetrievalPostProcessingMode.NONE,
            RetrievalPostProcessingMode.HEURISTIC_RERANK,
            RetrievalPostProcessingMode.MODEL_RERANK -> null
        }
    }

    private fun buildExplanation(candidate: SearchResultChunk, rerankScore: Double): String {
        return "lexical=${"%.3f".format(candidate.lexicalScore)}, vector=${"%.3f".format(candidate.vectorScore)}, fusion=${"%.3f".format(candidate.fusionScore)}, rerank=${"%.3f".format(rerankScore)}"
    }

    private fun SearchResultChunk.toRetrievedChunk(
        finalRank: Int?,
        filteredOut: Boolean,
        filterReason: String?,
        explanation: String?
    ): RetrievedContextChunk {
        return RetrievedContextChunk(
            chunkId = chunkId,
            source = relativePath.substringBefore('/').ifBlank { relativePath },
            title = title,
            relativePath = relativePath,
            section = section,
            finalRank = finalRank,
            score = fusionScore,
            semanticScore = semanticScore,
            keywordScore = keywordScore,
            lexicalScore = lexicalScore,
            vectorScore = vectorScore,
            fusionScore = fusionScore,
            candidateSource = candidateSource,
            rerankScore = null,
            excerpt = text.take(280),
            fullText = text,
            filteredOut = filteredOut,
            filterReason = filterReason,
            explanation = explanation,
            metadata = metadata
        )
    }

    private fun ProcessedCandidate.toRetrievedChunk(
        finalRank: Int?,
        filteredOut: Boolean,
        filterReason: String?,
        explanation: String?
    ): RetrievedContextChunk {
        return chunk.toRetrievedChunk(
            finalRank = finalRank,
            filteredOut = filteredOut,
            filterReason = filterReason,
            explanation = explanation
        ).copy(rerankScore = rerankScore)
    }

    private data class ProcessedCandidate(
        val chunk: SearchResultChunk,
        val rerankScore: Double,
        val explanation: String
    )

    private companion object {
        val modelModes = setOf(
            RetrievalPostProcessingMode.MODEL_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK
        )
    }
}

data class PostProcessingResult(
    val finalCandidates: List<RetrievedContextChunk>,
    val filteredCandidates: List<RetrievedContextChunk>,
    val fallbackApplied: Boolean,
    val fallbackReason: String?,
    val rerankExecution: RerankExecutionInfo
)

data class RerankExecutionInfo(
    val provider: String? = null,
    val model: String? = null,
    val applied: Boolean = false,
    val inputCount: Int = 0,
    val outputCount: Int = 0,
    val scoreThreshold: Double? = null,
    val timeoutMs: Long? = null,
    val fallbackUsed: Boolean = false,
    val fallbackReason: String? = null
)
