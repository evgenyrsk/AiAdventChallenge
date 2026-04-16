package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.SearchResultChunk
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
}
