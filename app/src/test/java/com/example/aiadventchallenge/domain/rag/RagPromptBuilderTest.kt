package com.example.aiadventchallenge.domain.rag

import com.example.aiadventchallenge.domain.model.RagAnswerPolicy
import com.example.aiadventchallenge.domain.model.RagContextChunk
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
        assertTrue(prompt.userPrompt.contains("Retrieved Context:"))
        assertTrue(prompt.userPrompt.contains("1.6-2.2 г/кг"))
        assertEquals(2, prompt.retrievalSummary.selectedCount)
        assertEquals("protein_guide.md", prompt.retrievalSummary.chunks.first().title)
    }
}
