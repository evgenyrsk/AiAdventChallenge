package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RagPromptBuilderTest {

    private val builder = RagPromptBuilder()

    @Test
    fun `build includes question context and strict instruction`() {
        val retrieval = RagRetrievalResult(
            query = "Сколько белка нужно при похудении?",
            originalQuery = "Сколько белка нужно при похудении?",
            rewrittenQuery = "белок protein intake снижение веса сохранение мышц",
            effectiveQuery = "белок protein intake снижение веса сохранение мышц",
            source = "fitness_knowledge",
            strategy = "structure_aware",
            selectedCount = 2,
            totalChars = 420,
            contextText = "[protein_guide.md | Practical intake ranges | score=0.913]\nОриентир 1.6-2.2 г/кг",
            chunks = listOf(
                RagContextChunk(
                    chunkId = "chunk-1",
                    title = "protein_guide.md",
                    relativePath = "nutrition/protein_guide.md",
                    section = "Practical intake ranges",
                    score = 0.913,
                    semanticScore = 0.88,
                    keywordScore = 1.0,
                    text = "Ориентир 1.6-2.2 г/кг"
                )
            ),
            initialCandidates = emptyList(),
            finalCandidates = listOf(
                RagContextChunk(
                    chunkId = "chunk-1",
                    title = "protein_guide.md",
                    relativePath = "nutrition/protein_guide.md",
                    section = "Practical intake ranges",
                    score = 0.913,
                    semanticScore = 0.88,
                    keywordScore = 1.0,
                    rerankScore = 0.95,
                    text = "Ориентир 1.6-2.2 г/кг"
                )
            ),
            filteredCandidates = listOf(
                RagContextChunk(
                    chunkId = "chunk-2",
                    title = "faq.md",
                    relativePath = "faq/fitness_faq.md",
                    section = "General",
                    score = 0.4,
                    semanticScore = 0.2,
                    keywordScore = 0.1,
                    rerankScore = 0.1,
                    text = "Шум",
                    filteredOut = true,
                    filterReason = "below_similarity_threshold",
                    explanation = "semantic=0.200"
                )
            ),
            debug = RagRetrievalDebug(
                topKBeforeFilter = 4,
                finalTopK = 2,
                similarityThreshold = 0.2,
                postProcessingMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                fallbackApplied = false,
                fallbackReason = null
            ),
            contextEnvelope = "Envelope"
        )

        val prompt = builder.build(
            question = "Сколько белка нужно при похудении?",
            retrieval = retrieval,
            policy = RagAnswerPolicy.STRICT
        )

        assertTrue(prompt.systemPromptSuffix.contains("RAG MODE"))
        assertTrue(prompt.systemPromptSuffix.contains("не додумывай факты"))
        assertTrue(prompt.userPrompt.contains("Вопрос пользователя:"))
        assertTrue(prompt.userPrompt.contains("Rewritten retrieval query"))
        assertTrue(prompt.userPrompt.contains("Retrieved Context:"))
        assertTrue(prompt.userPrompt.contains("1.6-2.2 г/кг"))
        assertEquals(2, prompt.retrievalSummary.selectedCount)
        assertEquals("protein_guide.md", prompt.retrievalSummary.chunks.first().title)
        assertEquals(4, prompt.retrievalSummary.topKBeforeFilter)
    }
}
