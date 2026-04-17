package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalDebugInfo
import com.example.mcp.server.documentindex.model.RetrievalPipelineConfig
import com.example.mcp.server.documentindex.model.RetrievalPostProcessingMode
import com.example.mcp.server.documentindex.model.RetrieveRelevantChunksResult
import com.example.mcp.server.documentindex.model.RetrievedContextChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerabilityGateTest {

    private val gate = AnswerabilityGate()

    @Test
    fun `fails when top semantic score is below threshold`() {
        val decision = gate.evaluate(
            retrieval = retrieval(
                finalCandidates = listOf(
                    chunk(semanticScore = 0.15, rerankScore = 0.12)
                )
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                topKBeforeFilter = 4,
                finalTopK = 1,
                similarityThreshold = 0.2,
                rerankScoreThreshold = 0.2,
                minAnswerableChunks = 1,
                allowAnswerWithRetrievalFallback = false
            )
        )

        assertFalse(decision.answerable)
        assertEquals("low_relevance", decision.reason)
    }

    @Test
    fun `passes when chunk count and scores are sufficient`() {
        val decision = gate.evaluate(
            retrieval = retrieval(
                finalCandidates = listOf(
                    chunk(semanticScore = 0.72, rerankScore = 0.81)
                )
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                topKBeforeFilter = 4,
                finalTopK = 1,
                similarityThreshold = 0.2,
                rerankScoreThreshold = 0.2,
                minAnswerableChunks = 1,
                allowAnswerWithRetrievalFallback = false
            )
        )

        assertTrue(decision.answerable)
    }

    @Test
    fun `fails when enhanced policy requires at least two final chunks`() {
        val decision = gate.evaluate(
            retrieval = retrieval(
                finalCandidates = listOf(
                    chunk(semanticScore = 0.72, rerankScore = 0.81)
                )
            ),
            config = RetrievalPipelineConfig(
                postProcessingEnabled = true,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                topKBeforeFilter = 4,
                finalTopK = 1,
                similarityThreshold = 0.2,
                rerankScoreThreshold = 0.2,
                minAnswerableChunks = 2,
                allowAnswerWithRetrievalFallback = false
            )
        )

        assertFalse(decision.answerable)
        assertEquals("no_relevant_chunks", decision.reason)
    }

    private fun retrieval(finalCandidates: List<RetrievedContextChunk>): RetrieveRelevantChunksResult {
        return RetrieveRelevantChunksResult(
            query = "q",
            originalQuery = "q",
            effectiveQuery = "q",
            source = "fitness_knowledge",
            strategy = "structure_aware",
            topK = 4,
            selectedCount = finalCandidates.size,
            totalChars = 120,
            contextText = finalCandidates.joinToString("\n") { it.excerpt },
            chunks = finalCandidates,
            finalCandidates = finalCandidates,
            debug = RetrievalDebugInfo(
                originalQuery = "q",
                effectiveQuery = "q",
                topKBeforeFilter = 4,
                finalTopK = finalCandidates.size,
                similarityThreshold = 0.2,
                postProcessingMode = RetrievalPostProcessingMode.THRESHOLD_PLUS_MODEL_RERANK,
                fallbackApplied = false
            ),
            contextEnvelope = "Envelope"
        )
    }

    private fun chunk(semanticScore: Double, rerankScore: Double): RetrievedContextChunk {
        return RetrievedContextChunk(
            chunkId = "chunk-1",
            source = "fitness_knowledge",
            title = "doc.md",
            relativePath = "nutrition/doc.md",
            section = "Section",
            finalRank = 1,
            score = semanticScore,
            semanticScore = semanticScore,
            keywordScore = 0.4,
            rerankScore = rerankScore,
            excerpt = "text",
            fullText = "text"
        )
    }
}
