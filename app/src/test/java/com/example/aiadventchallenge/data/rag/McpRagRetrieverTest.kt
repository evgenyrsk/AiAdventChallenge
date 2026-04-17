package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.RagPipelineConfig
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpRagRetrieverTest {

    private val repository = mockk<MultiServerRepository>()
    private val retriever = McpRagRetriever(repository)

    @Test
    fun `retrieve parses response with root data payload`() = runTest {
        coEvery {
            repository.callTool("retrieve_relevant_chunks", any())
        } returns McpToolData.StringResult(
            """
            {
              "message": "Retrieved relevant chunks for fitness_knowledge",
                "data": {
                  "query": "Что важнее для похудения?",
                  "originalQuery": "Что важнее для похудения?",
                  "rewrittenQuery": "дефицит калорий energy balance meal timing время приема пищи",
                  "effectiveQuery": "дефицит калорий energy balance meal timing время приема пищи",
                  "source": "fitness_knowledge",
                  "strategy": "structure_aware",
                  "selectedCount": 1,
                  "totalChars": 120,
                  "contextText": "Для снижения веса важнее дефицит калорий.",
                  "contextEnvelope": "Envelope",
                  "initialCandidates": [],
                  "finalCandidates": [],
                  "filteredCandidates": [],
                  "debug": {
                    "originalQuery": "Что важнее для похудения?",
                    "rewrittenQuery": "дефицит калорий energy balance meal timing время приема пищи",
                    "effectiveQuery": "дефицит калорий energy balance meal timing время приема пищи",
                    "topKBeforeFilter": 6,
                    "finalTopK": 4,
                    "similarityThreshold": 0.2,
                    "postProcessingMode": "THRESHOLD_PLUS_RERANK",
                    "rewriteApplied": true,
                    "detectedIntent": "FAT_LOSS_PRIORITY",
                    "rewriteStrategy": "INTENT_EXPANSION",
                    "addedTerms": ["energy balance", "meal timing"],
                    "removedPhrases": ["подскажи"],
                    "fallbackApplied": false
                  },
                  "chunks": [
                    {
                      "chunkId": "chunk-1",
                    "source": "fitness_knowledge",
                    "title": "fitness_faq.md",
                    "relativePath": "faq/fitness_faq.md",
                    "section": "Что важнее",
                    "finalRank": 1,
                    "score": 1.35,
                    "semanticScore": 0.81,
                    "keywordScore": 3.5,
                    "excerpt": "Для снижения веса важнее дефицит калорий.",
                    "fullText": "Для снижения веса важнее дефицит калорий. Время приема пищи вторично."
                  }
                ],
                "grounding": {
                  "sources": [
                    {
                      "source": "fitness_knowledge",
                      "title": "fitness_faq.md",
                      "section": "Что важнее",
                      "chunkId": "chunk-1",
                      "similarityScore": 1.35,
                      "relativePath": "faq/fitness_faq.md"
                    }
                  ],
                  "quotes": [
                    {
                      "quotedText": "Для снижения веса важнее дефицит калорий.",
                      "source": "fitness_knowledge",
                      "section": "Что важнее",
                      "chunkId": "chunk-1"
                    }
                  ],
                  "confidence": {
                    "answerable": true,
                    "minAnswerableChunks": 1,
                    "finalChunkCount": 1
                  }
                }
              }
            }
            """.trimIndent()
        )

        val result = retriever.retrieve(request())

        assertEquals("fitness_knowledge", result.source)
        assertEquals("structure_aware", result.strategy)
        assertEquals(1, result.selectedCount)
        assertEquals("fitness_faq.md", result.chunks.single().title)
        assertEquals("Envelope", result.contextEnvelope)
        assertEquals("дефицит калорий energy balance meal timing время приема пищи", result.effectiveQuery)
        assertEquals(6, result.debug.topKBeforeFilter)
        assertTrue(result.debug.rewriteApplied)
        assertEquals("FAT_LOSS_PRIORITY", result.debug.detectedIntent)
        assertTrue(result.grounding?.sources?.isNotEmpty() == true)
        assertTrue(result.grounding?.quotes?.isNotEmpty() == true)
    }

    @Test
    fun `retrieve parses response with json rpc result data payload`() = runTest {
        coEvery {
            repository.callTool("retrieve_relevant_chunks", any())
        } returns McpToolData.StringResult(
            """
            {
              "jsonrpc": "2.0",
              "result": {
                "message": "Retrieved relevant chunks for fitness_knowledge",
                "data": {
                  "query": "Что важнее для похудения?",
                  "originalQuery": "Что важнее для похудения?",
                  "effectiveQuery": "Что важнее для похудения?",
                  "source": "fitness_knowledge",
                  "strategy": "structure_aware",
                  "selectedCount": 1,
                  "totalChars": 120,
                  "contextText": "Для снижения веса важнее дефицит калорий.",
                  "initialCandidates": [],
                  "finalCandidates": [],
                  "filteredCandidates": [],
                  "debug": {
                    "originalQuery": "Что важнее для похудения?",
                    "effectiveQuery": "Что важнее для похудения?",
                    "topKBeforeFilter": 4,
                    "finalTopK": 4,
                    "postProcessingMode": "NONE",
                    "fallbackApplied": false
                  },
                  "chunks": [
                    {
                      "chunkId": "chunk-1",
                      "source": "fitness_knowledge",
                      "title": "fitness_faq.md",
                      "relativePath": "faq/fitness_faq.md",
                      "section": "Что важнее",
                      "finalRank": 1,
                      "score": 1.35,
                      "semanticScore": 0.81,
                      "keywordScore": 3.5,
                      "excerpt": "Для снижения веса важнее дефицит калорий.",
                      "fullText": "Для снижения веса важнее дефицит калорий."
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val result = retriever.retrieve(request())

        assertEquals("Для снижения веса важнее дефицит калорий.", result.contextText)
        assertEquals("Что важнее", result.chunks.single().section)
    }

    @Test
    fun `retrieve fails with clear error when data payload is missing`() = runTest {
        coEvery {
            repository.callTool("retrieve_relevant_chunks", any())
        } returns McpToolData.StringResult("""{"message":"no data"}""")

        val error = runCatching {
            retriever.retrieve(request())
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Missing data payload in retrieval response", error?.message)
    }

    private fun request(): RagRetrievalRequest {
        return RagRetrievalRequest(
            originalQuery = "Что важнее для похудения?",
            rewrittenQuery = null,
            effectiveQuery = "Что важнее для похудения?",
            config = RagPipelineConfig(
                source = "fitness_knowledge",
                strategy = "structure_aware",
                rewriteEnabled = false,
                postProcessingEnabled = false,
                postProcessingMode = RagPostProcessingMode.NONE,
                retrievalTopKBeforeFilter = 4,
                retrievalTopKAfterFilter = 4,
                similarityThreshold = null,
                minAnswerableChunks = 1,
                allowAnswerWithRetrievalFallback = true,
                maxChars = 2500,
                perDocumentLimit = 1,
                fallbackOnEmptyPostProcessing = true
            )
        )
    }
}
