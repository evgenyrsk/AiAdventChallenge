package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.mcp.DeveloperDocsRagConfig
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.PromptProfile
import com.example.aiadventchallenge.domain.model.RagPipelineConfig
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.RagRetriever
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperDocsRetrieverTest {
    private val prepareRagRequestUseCase = mockk<PrepareRagRequestUseCase>()
    private val ragRetriever = mockk<RagRetriever>()
    private val retriever = DeveloperDocsRetriever(prepareRagRequestUseCase, ragRetriever)

    @Test
    fun `uses project docs source`() = runTest {
        coEvery { prepareRagRequestUseCase.invoke(any(), any(), any(), any(), any()) } returns PreparedRagRequest(
            systemPromptSuffix = "",
            userPrompt = "",
            retrievalSummary = com.example.aiadventchallenge.domain.mcp.RetrievalSummary(
                query = "q",
                originalQuery = "q",
                effectiveQuery = "q",
                source = "project_docs",
                strategy = "structure_aware",
                selectedCount = 0,
                topKBeforeFilter = 4,
                finalTopK = 4,
                contextEnvelope = "",
                chunks = emptyList()
            )
        )
        coEvery { ragRetriever.retrieve(any()) } returns RagRetrievalResult(
            query = "q",
            originalQuery = "q",
            effectiveQuery = "q",
            source = "project_docs",
            strategy = "structure_aware",
            selectedCount = 0,
            totalChars = 0,
            contextText = "",
            chunks = emptyList(),
            debug = RagRetrievalDebug(
                topKBeforeFilter = 4,
                finalTopK = 4,
                postProcessingMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                fallbackApplied = false
            ),
            contextEnvelope = ""
        )

        val result = retriever.retrieve("where is chat flow", PromptProfile.OPTIMIZED_RAG)

        assertEquals("project_docs", result.retrieval.source)
        coVerify {
            ragRetriever.retrieve(withArg<RagRetrievalRequest> {
                assertEquals(DeveloperDocsRagConfig.PROJECT_DOCS_SOURCE, it.config.source)
            })
        }
    }
}
