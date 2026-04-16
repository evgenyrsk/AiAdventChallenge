package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RetrievedContextChunk
import com.example.mcp.server.documentindex.model.SearchResultChunk
import com.example.mcp.server.documentindex.rerank.EffectiveRerankStrategy
import com.example.mcp.server.documentindex.rerank.RerankExecutionResult
import com.example.mcp.server.documentindex.rerank.RerankService

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
    private val rerankService: RerankService = RerankService()
) : RetrievalPostProcessor {

    override fun process(
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String,
        candidates: List<SearchResultChunk>,
        config: RetrievalPipelineConfig
    ): PostProcessingResult {
        val mode = if (config.postProcessingEnabled) {
            config.postProcessingMode
        } else {
            RetrievalPostProcessingMode.NONE
        }

        val rerankExecution = when (mode) {
            RetrievalPostProcessingMode.MODEL_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK -> rerankService.rerank(
                originalQuery = originalQuery,
                rewrittenQuery = rewrittenQuery,
                effectiveQuery = effectiveQuery,
                candidates = candidates,
                config = config,
                heuristicScore = { candidate ->
                    heuristicRerankScore(candidate, originalQuery, rewrittenQuery, effectiveQuery)
                }
            )

            RetrievalPostProcessingMode.HEURISTIC_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK -> RerankExecutionResult(
                strategy = EffectiveRerankStrategy.HEURISTIC,
                applied = true,
                inputCount = candidates.size,
                outputCount = candidates.size.coerceAtMost(config.finalTopK.coerceAtLeast(1)),
                scoreByChunkId = candidates.associate { candidate ->
                    candidate.chunkId to heuristicRerankScore(candidate, originalQuery, rewrittenQuery, effectiveQuery)
                },
                orderedChunkIds = candidates
                    .sortedByDescending { heuristicRerankScore(it, originalQuery, rewrittenQuery, effectiveQuery) }
                    .map { it.chunkId }
            )

            RetrievalPostProcessingMode.NONE,
            RetrievalPostProcessingMode.THRESHOLD_ONLY -> RerankExecutionResult(
                strategy = EffectiveRerankStrategy.RETRIEVAL,
                applied = false,
                inputCount = candidates.size,
                outputCount = candidates.size.coerceAtMost(config.finalTopK.coerceAtLeast(1)),
                scoreByChunkId = candidates.associate { it.chunkId to it.score },
                orderedChunkIds = candidates.sortedByDescending { it.score }.map { it.chunkId }
            )
        }

        val scored = candidates.map { candidate ->
            val rerankScore = rerankExecution.scoreByChunkId[candidate.chunkId] ?: when (mode) {
                RetrievalPostProcessingMode.HEURISTIC_RERANK,
                RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK -> {
                    heuristicRerankScore(candidate, originalQuery, rewrittenQuery, effectiveQuery)
                }

                RetrievalPostProcessingMode.MODEL_RERANK,
                RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                RetrievalPostProcessingMode.NONE,
                RetrievalPostProcessingMode.THRESHOLD_ONLY -> candidate.score
            }
            ProcessedCandidate(
                chunk = candidate,
                rerankScore = rerankScore,
                explanation = buildExplanation(candidate, rerankScore)
            )
        }

        val thresholded = scored.mapNotNull { candidate ->
            val filteredReason = thresholdReason(candidate, mode, config)
            if (filteredReason == null) {
                candidate
            } else {
                null
            }
        }

        val ranked = when (mode) {
            RetrievalPostProcessingMode.HEURISTIC_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK,
            RetrievalPostProcessingMode.MODEL_RERANK,
            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK -> thresholded.sortedWith(
                compareByDescending<ProcessedCandidate> { rerankExecution.orderedChunkIds.indexOf(it.chunk.chunkId).takeIf { index -> index >= 0 }?.let { 10_000 - it } ?: 0 }
                    .thenByDescending { it.rerankScore }
            )
            RetrievalPostProcessingMode.NONE,
            RetrievalPostProcessingMode.THRESHOLD_ONLY -> thresholded.sortedByDescending { it.chunk.score }
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
                rerankExecution = rerankExecution
            )
        }

        if (!config.fallbackOnEmptyPostProcessing || candidates.isEmpty()) {
            return PostProcessingResult(
                finalCandidates = emptyList(),
                filteredCandidates = filtered,
                fallbackApplied = false,
                fallbackReason = null,
                rerankExecution = rerankExecution
            )
        }

        val fallbackOrdered = when (rerankExecution.strategy) {
            EffectiveRerankStrategy.HEURISTIC -> {
                candidates.sortedByDescending {
                    rerankExecution.scoreByChunkId[it.chunkId] ?: heuristicRerankScore(it, originalQuery, rewrittenQuery, effectiveQuery)
                }
            }

            else -> candidates.sortedByDescending { it.score }
        }
        val fallback = fallbackOrdered
            .take(config.finalTopK.coerceAtLeast(1))
            .mapIndexed { index, candidate ->
                val rerankScore = heuristicRerankScore(candidate, originalQuery, rewrittenQuery, effectiveQuery)
                RetrievedContextChunk(
                    chunkId = candidate.chunkId,
                    title = candidate.title,
                    relativePath = candidate.relativePath,
                    section = candidate.section,
                    finalRank = index + 1,
                    score = candidate.score,
                    semanticScore = candidate.semanticScore,
                    keywordScore = candidate.keywordScore,
                    rerankScore = rerankScore,
                    excerpt = candidate.text.take(280),
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
            rerankExecution = rerankExecution.copy(
                fallbackUsed = true,
                fallbackReason = rerankExecution.fallbackReason ?: "post_processing_removed_all_candidates"
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
                if (candidate.chunk.semanticScore < threshold) "below_similarity_threshold" else null
            }

            RetrievalPostProcessingMode.THRESHOLD_PLUS_RERANK -> {
                val threshold = config.similarityThreshold ?: return null
                if (candidate.rerankScore < threshold) "below_similarity_threshold" else null
            }

            RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK -> {
                val rerankThreshold = config.rerankScoreThreshold ?: config.similarityThreshold ?: return null
                if (candidate.rerankScore < rerankThreshold) "below_rerank_threshold" else null
            }

            RetrievalPostProcessingMode.NONE,
            RetrievalPostProcessingMode.HEURISTIC_RERANK,
            RetrievalPostProcessingMode.MODEL_RERANK -> null
        }
    }

    private fun heuristicRerankScore(
        candidate: SearchResultChunk,
        originalQuery: String,
        rewrittenQuery: String?,
        effectiveQuery: String
    ): Double {
        val originalTerms = tokenize(originalQuery)
        val rewrittenTerms = tokenize(rewrittenQuery.orEmpty())
        val effectiveTerms = tokenize(effectiveQuery)
        val combinedTerms = (originalTerms + rewrittenTerms + effectiveTerms).toSet()
        val haystack = buildString {
            append(candidate.title.lowercase())
            append(' ')
            append(candidate.section.lowercase())
            append(' ')
            append(candidate.relativePath.lowercase())
            append(' ')
            append(candidate.text.lowercase())
        }

        val overlap = if (combinedTerms.isEmpty()) {
            0.0
        } else {
            combinedTerms.count { haystack.contains(it) }.toDouble() / combinedTerms.size.toDouble()
        }
        val titleSectionBonus = combinedTerms.count {
            candidate.title.lowercase().contains(it) || candidate.section.lowercase().contains(it)
        } * 0.08
        val weakOverlapPenalty = if (overlap < 0.15) 0.12 else 0.0

        return (candidate.semanticScore * 0.7) +
            (candidate.keywordScore * 0.2) +
            (overlap * 0.25) +
            titleSectionBonus -
            weakOverlapPenalty
    }

    private fun buildExplanation(candidate: SearchResultChunk, rerankScore: Double): String {
        return "semantic=${"%.3f".format(candidate.semanticScore)}, keyword=${"%.3f".format(candidate.keywordScore)}, rerank=${"%.3f".format(rerankScore)}"
    }

    private fun tokenize(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}_]+"))
        .filter { it.length >= 3 }
        .toSet()

    private fun ProcessedCandidate.toRetrievedChunk(
        finalRank: Int?,
        filteredOut: Boolean,
        filterReason: String?,
        explanation: String?
    ): RetrievedContextChunk {
        return RetrievedContextChunk(
            chunkId = chunk.chunkId,
            title = chunk.title,
            relativePath = chunk.relativePath,
            section = chunk.section,
            finalRank = finalRank,
            score = chunk.score,
            semanticScore = chunk.semanticScore,
            keywordScore = chunk.keywordScore,
            rerankScore = rerankScore,
            excerpt = chunk.text.take(280),
            filteredOut = filteredOut,
            filterReason = filterReason,
            explanation = explanation
        )
    }

    private data class ProcessedCandidate(
        val chunk: SearchResultChunk,
        val rerankScore: Double,
        val explanation: String
    )
}

data class PostProcessingResult(
    val finalCandidates: List<RetrievedContextChunk>,
    val filteredCandidates: List<RetrievedContextChunk>,
    val fallbackApplied: Boolean,
    val fallbackReason: String?,
    val rerankExecution: RerankExecutionResult
)
