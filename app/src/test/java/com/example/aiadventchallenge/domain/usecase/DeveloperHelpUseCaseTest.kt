package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.GroundedSource
import com.example.aiadventchallenge.domain.model.PromptProfile
import com.example.aiadventchallenge.domain.model.RagConfidenceSummary
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagGrounding
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.RetrievalSummaryFactory
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.data.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeveloperHelpUseCaseTest {
    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val chatSettingsRepository = mockk<ChatSettingsRepository>()
    private val chatAgent = mockk<ChatAgent>()
    private val docsRetriever = mockk<DeveloperDocsRetriever>()
    private val promptAssembler = DeveloperHelpPromptAssembler()
    private val mcpRepository = mockk<MultiServerRepository>()

    private val useCase = DeveloperHelpUseCase(
        chatRepository = chatRepository,
        chatSettingsRepository = chatSettingsRepository,
        chatAgent = chatAgent,
        developerDocsRetriever = docsRetriever,
        promptAssembler = promptAssembler,
        mcpRepository = mcpRepository,
        localLlmProfileResolver = LocalLlmProfileResolver(),
        retrievalSummaryFactory = RetrievalSummaryFactory()
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @Test
    fun `returns deterministic help for empty question`() = runTest {
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.LOCAL_OLLAMA
        )

        val result = useCase("/help", "main", null)

        assertTrue(result.aiResponse.contains("Developer Help"))
        assertNull(result.retrievalSummary)
        coVerify(exactly = 0) { docsRetriever.retrieve(any(), any()) }
    }

    @Test
    fun `uses branch retrieval and llm for non empty question`() = runTest {
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.LOCAL_OLLAMA
        )
        coEvery { mcpRepository.callTool(any(), any()) } returns McpToolData.StringResult(
            """{"result":{"data":{"branch":"feature/dev-help","isGitRepo":true}}}"""
        )
        coEvery { docsRetriever.retrieve(any(), any()) } returns DeveloperDocsRetrievalResult(
            retrieval = RagRetrievalResult(
                query = "Как устроен RAG pipeline?",
                originalQuery = "Как устроен RAG pipeline?",
                effectiveQuery = "Как устроен RAG pipeline?",
                source = "project_docs",
                strategy = "structure_aware",
                selectedCount = 1,
                totalChars = 120,
                contextText = "RAG pipeline описан в README.md.",
                chunks = listOf(
                    RagContextChunk(
                        chunkId = "chunk-1",
                        source = "project_docs",
                        title = "README.md",
                        relativePath = "README.md",
                        section = "RAG",
                        score = 0.8,
                        semanticScore = 0.7,
                        keywordScore = 0.6,
                        text = "RAG pipeline описан в README.md."
                    )
                ),
                debug = RagRetrievalDebug(
                    topKBeforeFilter = 4,
                    finalTopK = 2,
                    postProcessingMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                    fallbackApplied = false
                ),
                contextEnvelope = "Envelope",
                grounding = RagGrounding(
                    sources = listOf(
                        GroundedSource(
                            title = "README.md",
                            relativePath = "README.md",
                            section = "RAG"
                        )
                    ),
                    confidence = RagConfidenceSummary(
                        answerable = true,
                        minAnswerableChunks = 1,
                        finalChunkCount = 1
                    )
                )
            ),
            retrievalLatencyMs = 25L
        )
        coEvery { chatAgent.processRequestWithContextAndUsage(any(), any(), any(), any()) } returns ChatResult.Success(
            AnswerWithUsage(
                content = "RAG pipeline проходит через MCP document index retrieval.",
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            )
        )

        val result = useCase("/help как устроен RAG pipeline?", "main", null)

        assertEquals("project_docs", result.retrievalSummary?.source)
        assertTrue(result.aiResponse.contains("Current branch: feature/dev-help"))
        assertTrue(result.answerPresentation.sources.isNotEmpty())
        coVerify { chatRepository.insertMessage(match { it.conversationMode.name == "DEVELOPER_HELP" }, any(), any()) }
    }
}
