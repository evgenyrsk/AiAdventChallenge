package com.example.aiadventchallenge.data.rag

import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.domain.mcp.McpToolData
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
                "source": "fitness_knowledge",
                "strategy": "structure_aware",
                "selectedCount": 1,
                "totalChars": 120,
                "contextText": "Для снижения веса важнее дефицит калорий.",
                "contextEnvelope": "Envelope",
                "chunks": [
                  {
                    "chunkId": "chunk-1",
                    "title": "fitness_faq.md",
                    "relativePath": "faq/fitness_faq.md",
                    "section": "Что важнее",
                    "score": 1.35,
                    "semanticScore": 0.81,
                    "keywordScore": 3.5,
                    "excerpt": "Для снижения веса важнее дефицит калорий."
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val result = retriever.retrieve(
            query = "Что важнее для похудения?",
            source = "fitness_knowledge",
            strategy = "structure_aware",
            topK = 4,
            maxChars = 2500,
            perDocumentLimit = 1
        )

        assertEquals("fitness_knowledge", result.source)
        assertEquals("structure_aware", result.strategy)
        assertEquals(1, result.selectedCount)
        assertEquals("fitness_faq.md", result.chunks.single().title)
        assertEquals("Envelope", result.contextEnvelope)
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
                  "source": "fitness_knowledge",
                  "strategy": "structure_aware",
                  "selectedCount": 1,
                  "totalChars": 120,
                  "contextText": "Для снижения веса важнее дефицит калорий.",
                  "chunks": [
                    {
                      "chunkId": "chunk-1",
                      "title": "fitness_faq.md",
                      "relativePath": "faq/fitness_faq.md",
                      "section": "Что важнее",
                      "score": 1.35,
                      "semanticScore": 0.81,
                      "keywordScore": 3.5,
                      "excerpt": "Для снижения веса важнее дефицит калорий."
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val result = retriever.retrieve(
            query = "Что важнее для похудения?",
            source = "fitness_knowledge",
            strategy = "structure_aware",
            topK = 4,
            maxChars = 2500,
            perDocumentLimit = 1
        )

        assertEquals("Для снижения веса важнее дефицит калорий.", result.contextText)
        assertEquals("Что важнее", result.chunks.single().section)
    }

    @Test
    fun `retrieve fails with clear error when data payload is missing`() = runTest {
        coEvery {
            repository.callTool("retrieve_relevant_chunks", any())
        } returns McpToolData.StringResult("""{"message":"no data"}""")

        val error = runCatching {
            retriever.retrieve(
                query = "Что важнее для похудения?",
                source = "fitness_knowledge",
                strategy = "structure_aware",
                topK = 4,
                maxChars = 2500,
                perDocumentLimit = 1
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Missing data payload in retrieval response", error?.message)
    }
}
