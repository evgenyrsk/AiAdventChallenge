package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.RagPromptBuilder
import com.example.aiadventchallenge.domain.rag.RagRetriever
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrepareRagRequestUseCaseTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `use case delegates to retriever with fitness defaults`() = runTest {
        val fakeRetriever = object : RagRetriever {
            override suspend fun retrieve(
                query: String,
                source: String,
                strategy: String,
                topK: Int,
                maxChars: Int,
                perDocumentLimit: Int
            ): RagRetrievalResult {
                assertEquals(FitnessRagConfig.DEFAULT_SOURCE, source)
                assertEquals(FitnessRagConfig.DEFAULT_STRATEGY, strategy)
                assertEquals(FitnessRagConfig.DEFAULT_TOP_K, topK)
                assertEquals(FitnessRagConfig.DEFAULT_MAX_CHARS, maxChars)
                assertEquals(FitnessRagConfig.DEFAULT_PER_DOCUMENT_LIMIT, perDocumentLimit)

                return RagRetrievalResult(
                    query = query,
                    source = source,
                    strategy = strategy,
                    selectedCount = 1,
                    totalChars = 120,
                    contextText = "Белок 1.6-2.2 г/кг",
                    chunks = listOf(
                        RagContextChunk(
                            chunkId = "chunk-1",
                            title = "protein_guide.md",
                            relativePath = "nutrition/protein_guide.md",
                            section = "Practical intake ranges",
                            score = 0.9,
                            semanticScore = 0.85,
                            keywordScore = 1.0,
                            text = "Белок 1.6-2.2 г/кг"
                        )
                    ),
                    contextEnvelope = "Envelope"
                )
            }
        }

        val useCase = PrepareRagRequestUseCase(
            ragRetriever = fakeRetriever,
            ragPromptBuilder = RagPromptBuilder()
        )

        val result = useCase("Сколько белка нужно при похудении?")

        assertTrue(result.userPrompt.contains("Белок 1.6-2.2 г/кг"))
        assertEquals("fitness_knowledge", result.retrievalSummary.source)
        assertEquals(1, result.retrievalSummary.selectedCount)
    }
}
