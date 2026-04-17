package com.example.mcp.server.documentindex.retrieval

import com.example.mcp.server.documentindex.model.RetrievalConfidenceSummary
import com.example.mcp.server.documentindex.model.RetrievedContextChunk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvidenceAssemblerTest {

    private val assembler = EvidenceAssembler()

    @Test
    fun `collects sources and deduplicates by path section chunk`() {
        val grounding = assembler.assemble(
            originalQuery = "Сколько белка нужно при похудении",
            effectiveQuery = "белок похудение сохранение мышц",
            finalCandidates = listOf(
                chunk(
                    chunkId = "chunk-1",
                    section = "Protein intake",
                    text = "Для похудения часто рекомендуют 1.6-2.2 г белка на кг массы тела."
                ),
                chunk(
                    chunkId = "chunk-1",
                    section = "Protein intake",
                    text = "Для похудения часто рекомендуют 1.6-2.2 г белка на кг массы тела."
                )
            ),
            confidence = RetrievalConfidenceSummary(
                answerable = true,
                minAnswerableChunks = 1,
                finalChunkCount = 2
            )
        )

        assertEquals(1, grounding.sources.size)
        assertTrue(grounding.quotes.isNotEmpty())
    }

    @Test
    fun `fallback grounding omits quotes`() {
        val grounding = assembler.assemble(
            originalQuery = "Неизвестный вопрос",
            effectiveQuery = "Неизвестный вопрос",
            finalCandidates = listOf(
                chunk(
                    chunkId = "chunk-2",
                    section = "General",
                    text = "Общий текст без релевантного ответа."
                )
            ),
            confidence = RetrievalConfidenceSummary(
                answerable = false,
                reason = "low_relevance",
                minAnswerableChunks = 1,
                finalChunkCount = 1
            ),
            fallbackReason = "low_relevance",
            isFallbackIDontKnow = true
        )

        assertTrue(grounding.sources.isEmpty())
        assertTrue(grounding.quotes.isEmpty())
        assertTrue(grounding.isFallbackIDontKnow)
    }

    private fun chunk(
        chunkId: String,
        section: String,
        text: String
    ): RetrievedContextChunk {
        return RetrievedContextChunk(
            chunkId = chunkId,
            source = "fitness_knowledge",
            title = "protein_guide.md",
            relativePath = "nutrition/protein_guide.md",
            section = section,
            finalRank = 1,
            score = 0.95,
            semanticScore = 0.9,
            keywordScore = 0.8,
            rerankScore = 0.91,
            excerpt = text.take(80),
            fullText = text
        )
    }
}
