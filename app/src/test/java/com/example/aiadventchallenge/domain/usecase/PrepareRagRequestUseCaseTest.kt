package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.RagPipelineConfig
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.RagContextChunk
import com.example.aiadventchallenge.domain.model.RagRetrievalDebug
import com.example.aiadventchallenge.domain.model.RagRetrievalRequest
import com.example.aiadventchallenge.domain.model.RagRetrievalResult
import com.example.aiadventchallenge.domain.rag.DefaultQueryRewriter
import com.example.aiadventchallenge.domain.rag.RagPromptBuilder
import com.example.aiadventchallenge.domain.rag.RagRetriever
import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import com.example.aiadventchallenge.rag.memory.RagConversationContext
import com.example.aiadventchallenge.rag.rewrite.RewriteIntent
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
            override suspend fun retrieve(request: RagRetrievalRequest): RagRetrievalResult {
                assertEquals(FitnessRagConfig.DEFAULT_SOURCE, request.config.source)
                assertEquals(FitnessRagConfig.DEFAULT_STRATEGY, request.config.strategy)
                assertEquals(FitnessRagConfig.DEFAULT_TOP_K, request.config.retrievalTopKBeforeFilter)
                assertEquals(FitnessRagConfig.DEFAULT_MAX_CHARS, request.config.maxChars)
                assertEquals(FitnessRagConfig.DEFAULT_PER_DOCUMENT_LIMIT, request.config.perDocumentLimit)
                assertEquals("Сколько белка нужно при похудении?", request.originalQuery)

                return RagRetrievalResult(
                    query = request.effectiveQuery,
                    originalQuery = request.originalQuery,
                    rewrittenQuery = request.rewrittenQuery,
                    effectiveQuery = request.effectiveQuery,
                    source = request.config.source,
                    strategy = request.config.strategy,
                    selectedCount = 1,
                    totalChars = 120,
                    contextText = "Белок 1.6-2.2 г/кг",
                    chunks = listOf(
                        RagContextChunk(
                            chunkId = "chunk-1",
                            source = "fitness_knowledge",
                            title = "protein_guide.md",
                            relativePath = "nutrition/protein_guide.md",
                            section = "Practical intake ranges",
                            finalRank = 1,
                            score = 0.9,
                            semanticScore = 0.85,
                            keywordScore = 1.0,
                            text = "Белок 1.6-2.2 г/кг"
                        )
                    ),
                    initialCandidates = emptyList(),
                    finalCandidates = emptyList(),
                    filteredCandidates = emptyList(),
                    debug = RagRetrievalDebug(
                        topKBeforeFilter = request.config.retrievalTopKBeforeFilter,
                        finalTopK = request.config.retrievalTopKAfterFilter,
                        similarityThreshold = request.config.similarityThreshold,
                        postProcessingMode = request.config.postProcessingMode,
                        fallbackApplied = false,
                        fallbackReason = null
                    ),
                    contextEnvelope = "Envelope"
                )
            }
        }

        val useCase = PrepareRagRequestUseCase(
            ragRetriever = fakeRetriever,
            ragPromptBuilder = RagPromptBuilder(),
            rewriteQueryUseCase = RewriteQueryUseCase(DefaultQueryRewriter())
        )

        val result = useCase("Сколько белка нужно при похудении?")

        assertTrue(result.userPrompt.contains("Белок 1.6-2.2 г/кг"))
        assertEquals("fitness_knowledge", result.retrievalSummary.source)
        assertEquals(1, result.retrievalSummary.selectedCount)
    }

    @Test
    fun `use case rewrites query for enhanced config`() = runTest {
        var capturedRequest: RagRetrievalRequest? = null
        val fakeRetriever = object : RagRetriever {
            override suspend fun retrieve(request: RagRetrievalRequest): RagRetrievalResult {
                capturedRequest = request
                return RagRetrievalResult(
                    query = request.effectiveQuery,
                    originalQuery = request.originalQuery,
                    rewrittenQuery = request.rewrittenQuery,
                    effectiveQuery = request.effectiveQuery,
                    source = request.config.source,
                    strategy = request.config.strategy,
                    selectedCount = 0,
                    totalChars = 0,
                    contextText = "",
                    chunks = emptyList(),
                    initialCandidates = emptyList(),
                    finalCandidates = emptyList(),
                    filteredCandidates = emptyList(),
                    debug = RagRetrievalDebug(
                        topKBeforeFilter = request.config.retrievalTopKBeforeFilter,
                        finalTopK = request.config.retrievalTopKAfterFilter,
                        similarityThreshold = request.config.similarityThreshold,
                        postProcessingMode = request.config.postProcessingMode,
                        fallbackApplied = false,
                        fallbackReason = null
                    ),
                    contextEnvelope = ""
                )
            }
        }

        val useCase = PrepareRagRequestUseCase(
            ragRetriever = fakeRetriever,
            ragPromptBuilder = RagPromptBuilder(),
            rewriteQueryUseCase = RewriteQueryUseCase(DefaultQueryRewriter())
        )

        useCase(
            question = "Подскажи пожалуйста, почему сон влияет на восстановление и контроль аппетита?",
            config = RagPipelineConfig(
                source = FitnessRagConfig.DEFAULT_SOURCE,
                strategy = FitnessRagConfig.DEFAULT_STRATEGY,
                rewriteEnabled = true,
                postProcessingEnabled = true,
                postProcessingMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                retrievalTopKBeforeFilter = 6,
                retrievalTopKAfterFilter = 4,
                similarityThreshold = 0.2,
                minAnswerableChunks = 1,
                allowAnswerWithRetrievalFallback = false,
                maxChars = FitnessRagConfig.DEFAULT_MAX_CHARS,
                perDocumentLimit = FitnessRagConfig.DEFAULT_PER_DOCUMENT_LIMIT,
                fallbackOnEmptyPostProcessing = true
            )
        )

        assertEquals("Подскажи пожалуйста, почему сон влияет на восстановление и контроль аппетита?", capturedRequest?.originalQuery)
        assertTrue(capturedRequest?.rewrittenQuery?.contains("сон восстановление аппетит", ignoreCase = true) == true)
        assertEquals(RewriteIntent.SLEEP_RECOVERY_APPETITE, capturedRequest?.rewriteResult?.detectedIntent)
    }

    @Test
    fun `use case includes task state in retrieval input and prompt`() = runTest {
        var capturedRequest: RagRetrievalRequest? = null
        val fakeRetriever = object : RagRetriever {
            override suspend fun retrieve(request: RagRetrievalRequest): RagRetrievalResult {
                capturedRequest = request
                return RagRetrievalResult(
                    query = request.effectiveQuery,
                    originalQuery = request.originalQuery,
                    rewrittenQuery = request.rewrittenQuery,
                    effectiveQuery = request.effectiveQuery,
                    source = request.config.source,
                    strategy = request.config.strategy,
                    selectedCount = 1,
                    totalChars = 64,
                    contextText = "Контекст по белку и дефициту",
                    chunks = emptyList(),
                    initialCandidates = emptyList(),
                    finalCandidates = emptyList(),
                    filteredCandidates = emptyList(),
                    debug = RagRetrievalDebug(
                        topKBeforeFilter = request.config.retrievalTopKBeforeFilter,
                        finalTopK = request.config.retrievalTopKAfterFilter,
                        similarityThreshold = request.config.similarityThreshold,
                        postProcessingMode = request.config.postProcessingMode,
                        fallbackApplied = false,
                        fallbackReason = null
                    ),
                    contextEnvelope = "Envelope"
                )
            }
        }

        val useCase = PrepareRagRequestUseCase(
            ragRetriever = fakeRetriever,
            ragPromptBuilder = RagPromptBuilder(),
            rewriteQueryUseCase = RewriteQueryUseCase(DefaultQueryRewriter())
        )

        val result = useCase(
            question = "А что насчет белка?",
            config = FitnessRagConfig.enhancedPipeline,
            conversationContext = RagConversationContext(
                taskState = ConversationTaskState(
                    dialogGoal = "Снижение веса с удержанием мышц",
                    resolvedConstraints = listOf("Времени мало"),
                    userClarifications = listOf("Пользователь хочет держать дефицит"),
                    latestSummary = "Фокус на похудении без потери мышц"
                )
            )
        )

        assertEquals("Снижение веса с удержанием мышц", capturedRequest?.conversationGoal)
        assertTrue(capturedRequest?.retrievalHints?.isNotEmpty() == true)
        assertTrue(result.userPrompt.contains("Conversation Task State"))
        assertTrue(result.userPrompt.contains("Снижение веса"))
    }
}
