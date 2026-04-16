package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RetrievalRerankFallbackPolicy
import com.example.mcp.server.documentindex.model.SearchResultChunk
import com.example.mcp.server.documentindex.rerank.RerankClient
import com.example.mcp.server.documentindex.rerank.RerankDebugInfo
import com.example.mcp.server.documentindex.rerank.RerankRequest
import com.example.mcp.server.documentindex.rerank.RerankResponse
import com.example.mcp.server.documentindex.rerank.RerankedCandidate
import com.example.mcp.server.documentindex.rerank.RerankService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultRetrievalPostProcessorTest {

    private val processor = DefaultRetrievalPostProcessor()

    @Test
    fun `threshold filtering removes candidates below similarity threshold`() {
        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = null,
            effectiveQuery = "Почему сон влияет на аппетит",
            candidates = listOf(
                chunk("good", "sleep.md", "sleep appetite", semantic = 0.8, keyword = 0.4),
                chunk("bad", "misc.md", "misc", semantic = 0.1, keyword = 0.0)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_ONLY,
                topKBeforeFilter = 2,
                finalTopK = 2,
                similarityThreshold = 0.2,
                fallbackOnEmptyPostProcessing = false
            )
        )

        assertEquals(1, result.finalCandidates.size)
        assertEquals("good", result.finalCandidates.single().chunkId)
        assertEquals("below_similarity_threshold", result.filteredCandidates.single().filterReason)
    }

    @Test
    fun `heuristic reranking promotes title and section matches`() {
        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = "сон восстановление аппетит качество тренировки недосып",
            effectiveQuery = "сон восстановление аппетит качество тренировки недосып",
            candidates = listOf(
                chunk("generic", "general.md", "general advice", semantic = 0.9, keyword = 0.1),
                chunk("sleep", "sleep.md", "сон аппетит восстановление", semantic = 0.7, keyword = 0.5)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.HEURISTIC_RERANK,
                topKBeforeFilter = 2,
                finalTopK = 1,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertEquals("sleep", result.finalCandidates.single().chunkId)
        assertEquals("removed_by_final_topk", result.filteredCandidates.single().filterReason)
    }

    @Test
    fun `empty result after filtering falls back to initial candidates when enabled`() {
        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = null,
            effectiveQuery = "Почему сон влияет на аппетит",
            candidates = listOf(
                chunk("fallback", "sleep.md", "sleep appetite", semantic = 0.1, keyword = 0.1)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_ONLY,
                topKBeforeFilter = 1,
                finalTopK = 1,
                similarityThreshold = 0.9,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertTrue(result.fallbackApplied)
        assertEquals("fallback", result.finalCandidates.single().chunkId)
        assertEquals("fallback_restored", result.finalCandidates.single().filterReason)
    }

    @Test
    fun `model rerank reorders candidates using self hosted scores`() {
        val processor = DefaultRetrievalPostProcessor(
            rerankService = RerankService(
                client = object : RerankClient {
                    override fun rerank(request: RerankRequest): RerankResponse {
                        return RerankResponse(
                            provider = "local_http",
                            model = "BAAI/bge-reranker-base",
                            results = listOf(
                                reranked(chunkId = "sleep", score = 0.92, rank = 1),
                                reranked(chunkId = "generic", score = 0.31, rank = 2)
                            ),
                            debug = RerankDebugInfo(
                                inputCandidateCount = request.candidates.size,
                                outputCandidateCount = 2,
                                topKAfter = request.topKAfter
                            )
                        )
                    }
                }
            )
        )

        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = "сон восстановление аппетит качество тренировки недосып",
            effectiveQuery = "сон восстановление аппетит качество тренировки недосып",
            candidates = listOf(
                chunk("generic", "general.md", "general advice", semantic = 0.9, keyword = 0.1),
                chunk("sleep", "sleep.md", "сон аппетит восстановление", semantic = 0.7, keyword = 0.5)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.MODEL_RERANK,
                topKBeforeFilter = 2,
                finalTopK = 1,
                rerankEnabled = true,
                rerankTimeoutMs = 1500,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertEquals("sleep", result.finalCandidates.single().chunkId)
        assertEquals(1, result.finalCandidates.single().finalRank)
        assertTrue(result.rerankExecution.applied)
        assertEquals("local_http", result.rerankExecution.provider)
    }

    @Test
    fun `model rerank falls back to heuristic when service fails`() {
        val processor = DefaultRetrievalPostProcessor(
            rerankService = RerankService(
                client = object : RerankClient {
                    override fun rerank(request: RerankRequest): RerankResponse {
                        error("sidecar unavailable")
                    }
                }
            )
        )

        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = "сон восстановление аппетит качество тренировки недосып",
            effectiveQuery = "сон восстановление аппетит качество тренировки недосып",
            candidates = listOf(
                chunk("generic", "general.md", "general advice", semantic = 0.9, keyword = 0.1),
                chunk("sleep", "sleep.md", "сон аппетит восстановление", semantic = 0.7, keyword = 0.5)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.MODEL_RERANK,
                topKBeforeFilter = 2,
                finalTopK = 1,
                rerankEnabled = true,
                rerankFallbackPolicy = RetrievalRerankFallbackPolicy.HEURISTIC_THEN_RETRIEVAL,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertEquals("sleep", result.finalCandidates.single().chunkId)
        assertTrue(result.rerankExecution.fallbackUsed)
        assertTrue(result.rerankExecution.fallbackReason?.contains("model_rerank_failed") == true)
    }

    @Test
    fun `model rerank threshold filters low rerank scores`() {
        val processor = DefaultRetrievalPostProcessor(
            rerankService = RerankService(
                client = object : RerankClient {
                    override fun rerank(request: RerankRequest): RerankResponse {
                        return RerankResponse(
                            provider = "local_http",
                            model = "BAAI/bge-reranker-base",
                            results = listOf(
                                reranked(chunkId = "sleep", score = 0.55, rank = 1),
                                reranked(chunkId = "generic", score = 0.10, rank = 2)
                            ),
                            debug = RerankDebugInfo(
                                inputCandidateCount = request.candidates.size,
                                outputCandidateCount = 2,
                                topKAfter = request.topKAfter,
                                thresholdApplied = request.minScoreThreshold
                            )
                        )
                    }
                }
            )
        )

        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = null,
            effectiveQuery = "Почему сон влияет на аппетит",
            candidates = listOf(
                chunk("sleep", "sleep.md", "сон аппетит восстановление", semantic = 0.7, keyword = 0.5),
                chunk("generic", "general.md", "general advice", semantic = 0.9, keyword = 0.1)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                topKBeforeFilter = 2,
                finalTopK = 2,
                similarityThreshold = 0.2,
                rerankEnabled = true,
                rerankScoreThreshold = 0.3,
                fallbackOnEmptyPostProcessing = false
            )
        )

        assertEquals(1, result.finalCandidates.size)
        assertEquals("sleep", result.finalCandidates.single().chunkId)
        assertEquals("below_rerank_threshold", result.filteredCandidates.single().filterReason)
    }

    @Test
    fun `empty candidate list bypasses rerank safely`() {
        val processor = DefaultRetrievalPostProcessor(
            rerankService = RerankService(
                client = object : RerankClient {
                    override fun rerank(request: RerankRequest): RerankResponse {
                        error("rerank should not be called for empty candidates")
                    }
                }
            )
        )

        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = null,
            effectiveQuery = "Почему сон влияет на аппетит",
            candidates = emptyList(),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.MODEL_RERANK,
                topKBeforeFilter = 5,
                finalTopK = 3,
                rerankEnabled = true,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertTrue(result.finalCandidates.isEmpty())
        assertTrue(result.filteredCandidates.isEmpty())
        assertEquals(0, result.rerankExecution.inputCount)
    }

    @Test
    fun `model rerank can fall back to retrieval only policy`() {
        val processor = DefaultRetrievalPostProcessor(
            rerankService = RerankService(
                client = object : RerankClient {
                    override fun rerank(request: RerankRequest): RerankResponse {
                        error("sidecar unavailable")
                    }
                }
            )
        )

        val result = processor.process(
            originalQuery = "Почему сон влияет на аппетит",
            rewrittenQuery = "сон восстановление аппетит качество тренировки недосып",
            effectiveQuery = "сон восстановление аппетит качество тренировки недосып",
            candidates = listOf(
                chunk("generic", "general.md", "general advice", semantic = 0.9, keyword = 0.1),
                chunk("sleep", "sleep.md", "сон аппетит восстановление", semantic = 0.7, keyword = 0.5)
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.MODEL_RERANK,
                topKBeforeFilter = 2,
                finalTopK = 1,
                rerankEnabled = true,
                rerankFallbackPolicy = RetrievalRerankFallbackPolicy.RETRIEVAL_ONLY,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertEquals("generic", result.finalCandidates.single().chunkId)
        assertTrue(result.rerankExecution.fallbackUsed)
        assertEquals("model_rerank_failed:sidecar unavailable", result.rerankExecution.fallbackReason)
    }

    private fun chunk(
        chunkId: String,
        title: String,
        section: String,
        semantic: Double,
        keyword: Double
    ): SearchResultChunk {
        return SearchResultChunk(
            chunkId = chunkId,
            documentId = title,
            title = title,
            relativePath = "training/$title",
            section = section,
            chunkingStrategy = "structure_aware",
            documentType = "MARKDOWN",
            score = semantic * 0.8 + keyword * 0.2,
            semanticScore = semantic,
            keywordScore = keyword,
            text = "content about $section",
            positionStart = 0,
            positionEnd = 100,
            pageNumber = null
        )
    }

    private fun reranked(chunkId: String, score: Double, rank: Int): RerankedCandidate {
        return RerankedCandidate(
            chunkId = chunkId,
            rerankScore = score,
            rank = rank,
            title = "$chunkId.md",
            relativePath = "training/$chunkId.md",
            section = chunkId,
            retrievalScore = score,
            semanticScore = score,
            keywordScore = score
        )
    }
}
