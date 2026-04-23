package com.example.aiadventchallenge.domain.chat

import android.util.Log
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.context.ContextStrategy
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.GroundedSource
import com.example.aiadventchallenge.domain.model.GroundedAnswerPayload
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.RagAnswerMode
import com.example.aiadventchallenge.domain.model.RagConfidenceSummary
import com.example.aiadventchallenge.domain.model.RagPostProcessingMode
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.mcp.RetrievalSourceCard
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.usecase.PrepareRagRequestUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatMessageHandlerImplTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val agent = mockk<ChatAgent>()
    private val contextStrategyFactory = mockk<ContextStrategyFactory>()
    private val chatSettingsRepository = mockk<ChatSettingsRepository>()
    private val prepareRagRequestUseCase = mockk<PrepareRagRequestUseCase>()
    private val contextStrategy = mockk<ContextStrategy>()

    private val handler = ChatMessageHandlerImpl(
        chatRepository = chatRepository,
        agent = agent,
        contextStrategyFactory = contextStrategyFactory,
        chatSettingsRepository = chatSettingsRepository,
        prepareRagRequestUseCase = prepareRagRequestUseCase
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.REMOTE
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `generateAiResponse uses rag augmentation only in RAG mode`() = runTest {
        val userInput = "Почему сон влияет на аппетит?"
        val activeMessages = listOf(
            ChatMessage(
                id = "assistant-0",
                parentMessageId = null,
                content = "Предыдущий ответ\n\nИсточники:\n1. recovery_sleep_steps.md / Why sleep matters",
                isFromUser = false
            ),
            ChatMessage(
                id = "user-1",
                parentMessageId = "assistant-0",
                content = userInput,
                isFromUser = true
            )
        )
        val baseMessages = listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.ASSISTANT, "Предыдущий ответ"),
            Message(MessageRole.USER, userInput)
        )
        val preparedRagRequest = PreparedRagRequest(
            systemPromptSuffix = "RAG MODE\nне додумывай факты",
            userPrompt = "Вопрос пользователя:\n$userInput\n\nRetrieved Context:\nКонтекст",
            retrievalSummary = RetrievalSummary(
                query = userInput,
                originalQuery = userInput,
                rewrittenQuery = "сон восстановление аппетит качество тренировки недосып",
                effectiveQuery = "сон восстановление аппетит качество тренировки недосып",
                source = "fitness_knowledge",
                strategy = "structure_aware",
                selectedCount = 1,
                topKBeforeFilter = 6,
                finalTopK = 4,
                similarityThreshold = 0.2,
                postProcessingMode = "THRESHOLD_PLUS_RERANK",
                fallbackApplied = false,
                fallbackReason = null,
                contextEnvelope = "Envelope",
                chunks = listOf(
                    RetrievalSourceCard(
                        chunkId = "chunk-1",
                        title = "recovery_sleep_steps.md",
                        relativePath = "training/recovery_sleep_steps.md",
                        section = "Why sleep matters",
                        score = 0.92
                    )
                ),
                initialCandidates = emptyList(),
                filteredCandidates = emptyList(),
                groundedAnswer = GroundedAnswerPayload(
                    answerText = "",
                    sources = listOf(
                        GroundedSource(
                            title = "recovery_sleep_steps.md",
                            section = "Why sleep matters",
                            relativePath = "training/recovery_sleep_steps.md"
                        )
                    ),
                    quotes = emptyList(),
                    answerMode = RagAnswerMode.GROUNDED,
                    pipelineMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                    confidence = RagConfidenceSummary(
                        answerable = true,
                        minAnswerableChunks = 1,
                        finalChunkCount = 1
                    )
                )
            )
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns activeMessages
        coEvery { chatSettingsRepository.getSettings() } returns ContextStrategyConfig(ContextStrategyType.SLIDING_WINDOW)
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.LOCAL_OLLAMA
        )
        every { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { contextStrategy.buildContext(null, any(), any()) } returns listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.USER, userInput)
        )
        every { agent.buildRequestConfigWithProfile(any()) } returns RequestConfig(systemPrompt = "system")
        coEvery { prepareRagRequestUseCase.invoke(any(), any(), any(), any()) } returns preparedRagRequest

        val capturedMessages = mutableListOf<List<Message>>()
        val capturedContextMessages = mutableListOf<List<ChatMessage>>()
        coEvery { contextStrategy.buildContext(null, capture(capturedContextMessages), any()) } returns baseMessages
        coEvery {
            agent.processRequestWithContextAndUsage(
                messages = capture(capturedMessages),
                config = any(),
                userInput = userInput,
                taskContext = null
            )
        } returns ChatResult.Success(
            AnswerWithUsage(
                content = "Ответ",
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            )
        )

        val plainResult = handler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = "user-1",
            mcpContext = null,
            answerMode = AnswerMode.PLAIN_LLM
        ) as ChatMessageResult.Success

        val ragResult = handler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = "user-1",
            mcpContext = null,
            answerMode = AnswerMode.RAG_ENHANCED
        ) as ChatMessageResult.Success

        coVerify(exactly = 1) {
            prepareRagRequestUseCase.invoke(any(), any(), any(), any())
        }

        assertEquals("Предыдущий ответ", capturedContextMessages[0].first().content)
        assertFalse(capturedContextMessages[0].first().content.contains("Источники:"))
        assertEquals(userInput, capturedMessages[0].last().content)
        assertEquals(2, capturedMessages[1].size)
        assertEquals(MessageRole.SYSTEM.value, capturedMessages[1].first().role)
        assertTrue(capturedMessages[1].last().content.contains("Retrieved Context"))
        assertEquals("fitness_knowledge", ragResult.retrievalSummary?.source)
        assertEquals(null, plainResult.retrievalSummary)
        assertEquals("Ответ", ragResult.aiResponse)
        assertFalse(ragResult.aiResponse.contains("Источники:"))
        assertTrue(ragResult.retrievalSummary?.groundedAnswer?.sources?.isNotEmpty() == true)
        assertEquals(AiBackendType.LOCAL_OLLAMA, ragResult.executionInfo?.backend)
        assertEquals("aiMessage must carry source preview", 1, ragResult.answerPresentation?.sources?.size)
    }

    @Test
    fun `generateAiResponse sanitizes assistant history for plain llm`() = runTest {
        val userInput = "Сделай краткое резюме"
        val activeMessages = listOf(
            ChatMessage(
                id = "assistant-1",
                parentMessageId = null,
                content = "Белок важен для сохранения мышц.\n\nИсточники:\n1. protein_guide.md / Protein",
                isFromUser = false
            ),
            ChatMessage(
                id = "user-2",
                parentMessageId = "assistant-1",
                content = userInput,
                isFromUser = true
            )
        )
        val baseMessages = listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.ASSISTANT, "Белок важен для сохранения мышц."),
            Message(MessageRole.USER, userInput)
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns activeMessages
        coEvery { chatSettingsRepository.getSettings() } returns ContextStrategyConfig(ContextStrategyType.SLIDING_WINDOW)
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.REMOTE
        )
        every { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { contextStrategy.buildContext(null, any(), any()) } returns listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.USER, userInput)
        )
        every { agent.buildRequestConfigWithProfile(any()) } returns RequestConfig(systemPrompt = "system")

        val capturedContextMessages = mutableListOf<List<ChatMessage>>()
        coEvery { contextStrategy.buildContext(null, capture(capturedContextMessages), any()) } returns baseMessages
        coEvery {
            agent.processRequestWithContextAndUsage(
                messages = any(),
                config = any(),
                userInput = userInput,
                taskContext = null
            )
        } returns ChatResult.Success(
            AnswerWithUsage(
                content = "Краткое резюме",
                promptTokens = 4,
                completionTokens = 5,
                totalTokens = 9
            )
        )

        handler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = "user-2",
            mcpContext = null,
            answerMode = AnswerMode.PLAIN_LLM
        )

        assertEquals("Белок важен для сохранения мышц.", capturedContextMessages.single().first().content)
        assertFalse(capturedContextMessages.single().first().content.contains("Источники:"))
    }

    @Test
    fun `generateAiResponse strips trailing russian sources block from answer text`() = runTest {
        val userInput = "Почему важен белок?"
        val activeMessages = listOf(
            ChatMessage(
                id = "user-1",
                parentMessageId = null,
                content = userInput,
                isFromUser = true
            )
        )
        val baseMessages = listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.USER, userInput)
        )
        val preparedRagRequest = PreparedRagRequest(
            systemPromptSuffix = "RAG MODE",
            userPrompt = "Вопрос пользователя:\n$userInput\n\nRetrieved Context:\nКонтекст",
            retrievalSummary = RetrievalSummary(
                query = userInput,
                originalQuery = userInput,
                rewrittenQuery = null,
                effectiveQuery = userInput,
                source = "fitness_knowledge",
                strategy = "structure_aware",
                selectedCount = 1,
                topKBeforeFilter = 4,
                finalTopK = 2,
                similarityThreshold = 0.2,
                postProcessingMode = "THRESHOLD_PLUS_RERANK",
                fallbackApplied = false,
                fallbackReason = null,
                contextEnvelope = "Envelope",
                chunks = emptyList(),
                initialCandidates = emptyList(),
                filteredCandidates = emptyList(),
                groundedAnswer = GroundedAnswerPayload(
                    answerText = "",
                    sources = listOf(
                        GroundedSource(
                            title = "protein_guide.md",
                            section = "Protein",
                            relativePath = "nutrition/protein_guide.md"
                        )
                    ),
                    quotes = emptyList(),
                    answerMode = RagAnswerMode.GROUNDED,
                    pipelineMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                    confidence = RagConfidenceSummary(
                        answerable = true,
                        minAnswerableChunks = 1,
                        finalChunkCount = 1
                    )
                )
            )
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns activeMessages
        coEvery { chatSettingsRepository.getSettings() } returns ContextStrategyConfig(ContextStrategyType.SLIDING_WINDOW)
        every { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { contextStrategy.buildContext(null, any(), any()) } returns listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.USER, userInput)
        )
        every { agent.buildRequestConfigWithProfile(any()) } returns RequestConfig(systemPrompt = "system")
        coEvery { prepareRagRequestUseCase.invoke(any(), any(), any(), any()) } returns preparedRagRequest
        coEvery { contextStrategy.buildContext(null, any(), any()) } returns baseMessages
        coEvery {
            agent.processRequestWithContextAndUsage(
                messages = any(),
                config = any(),
                userInput = userInput,
                taskContext = null
            )
        } returns ChatResult.Success(
            AnswerWithUsage(
                content = "Белок помогает сохранять мышечную массу.\n\nИсточники:\n1. protein_guide.md / Protein",
                promptTokens = 8,
                completionTokens = 10,
                totalTokens = 18
            )
        )

        val result = handler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = "user-1",
            mcpContext = null,
            answerMode = AnswerMode.RAG_ENHANCED
        ) as ChatMessageResult.Success

        assertEquals("Белок помогает сохранять мышечную массу.", result.aiResponse)
        assertFalse(result.aiResponse.contains("Источники:"))
        assertTrue(result.retrievalSummary?.groundedAnswer?.sources?.isNotEmpty() == true)
    }

    @Test
    fun `generateAiResponse strips trailing english sources block from answer text`() = runTest {
        val userInput = "Why is protein important?"
        val activeMessages = listOf(
            ChatMessage(
                id = "user-1",
                parentMessageId = null,
                content = userInput,
                isFromUser = true
            )
        )
        val baseMessages = listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.USER, userInput)
        )
        val preparedRagRequest = PreparedRagRequest(
            systemPromptSuffix = "RAG MODE",
            userPrompt = "User question:\n$userInput\n\nRetrieved Context:\nContext",
            retrievalSummary = RetrievalSummary(
                query = userInput,
                originalQuery = userInput,
                rewrittenQuery = null,
                effectiveQuery = userInput,
                source = "fitness_knowledge",
                strategy = "structure_aware",
                selectedCount = 1,
                topKBeforeFilter = 4,
                finalTopK = 2,
                similarityThreshold = 0.2,
                postProcessingMode = "THRESHOLD_PLUS_RERANK",
                fallbackApplied = false,
                fallbackReason = null,
                contextEnvelope = "Envelope",
                chunks = emptyList(),
                initialCandidates = emptyList(),
                filteredCandidates = emptyList(),
                groundedAnswer = GroundedAnswerPayload(
                    answerText = "",
                    sources = listOf(
                        GroundedSource(
                            title = "protein_guide.md",
                            section = "Protein",
                            relativePath = "nutrition/protein_guide.md"
                        )
                    ),
                    quotes = emptyList(),
                    answerMode = RagAnswerMode.GROUNDED,
                    pipelineMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                    confidence = RagConfidenceSummary(
                        answerable = true,
                        minAnswerableChunks = 1,
                        finalChunkCount = 1
                    )
                )
            )
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns activeMessages
        coEvery { chatSettingsRepository.getSettings() } returns ContextStrategyConfig(ContextStrategyType.SLIDING_WINDOW)
        every { contextStrategyFactory.create(any()) } returns contextStrategy
        every { agent.buildRequestConfigWithProfile(any()) } returns RequestConfig(systemPrompt = "system")
        coEvery { prepareRagRequestUseCase.invoke(any(), any(), any(), any()) } returns preparedRagRequest
        coEvery { contextStrategy.buildContext(null, any(), any()) } returns baseMessages
        coEvery {
            agent.processRequestWithContextAndUsage(
                messages = any(),
                config = any(),
                userInput = userInput,
                taskContext = null
            )
        } returns ChatResult.Success(
            AnswerWithUsage(
                content = "Protein helps preserve lean mass.\n\nSources:\n1. protein_guide.md / Protein",
                promptTokens = 8,
                completionTokens = 10,
                totalTokens = 18
            )
        )

        val result = handler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = "user-1",
            mcpContext = null,
            answerMode = AnswerMode.RAG_ENHANCED
        ) as ChatMessageResult.Success

        assertEquals("Protein helps preserve lean mass.", result.aiResponse)
        assertFalse(result.aiResponse.contains("Sources:"))
        assertTrue(result.retrievalSummary?.groundedAnswer?.sources?.isNotEmpty() == true)
    }

    @Test
    fun `generateAiResponse fallback text stays clean without sources block`() = runTest {
        val userInput = "Что делать, если данных недостаточно?"
        val activeMessages = listOf(
            ChatMessage(
                id = "user-1",
                parentMessageId = null,
                content = userInput,
                isFromUser = true
            )
        )
        val preparedRagRequest = PreparedRagRequest(
            systemPromptSuffix = "RAG MODE",
            userPrompt = "Вопрос пользователя:\n$userInput",
            retrievalSummary = RetrievalSummary(
                query = userInput,
                originalQuery = userInput,
                rewrittenQuery = null,
                effectiveQuery = userInput,
                source = "fitness_knowledge",
                strategy = "structure_aware",
                selectedCount = 0,
                topKBeforeFilter = 4,
                finalTopK = 0,
                similarityThreshold = 0.2,
                postProcessingMode = "THRESHOLD_PLUS_RERANK",
                fallbackApplied = true,
                fallbackReason = "insufficient_context",
                contextEnvelope = "Envelope",
                chunks = emptyList(),
                initialCandidates = emptyList(),
                filteredCandidates = emptyList(),
                groundedAnswer = GroundedAnswerPayload(
                    answerText = "",
                    sources = emptyList(),
                    quotes = emptyList(),
                    answerMode = RagAnswerMode.FALLBACK_I_DONT_KNOW,
                    pipelineMode = RagPostProcessingMode.THRESHOLD_PLUS_RERANK,
                    confidence = RagConfidenceSummary(
                        answerable = false,
                        minAnswerableChunks = 1,
                        finalChunkCount = 0
                    ),
                    fallbackReason = "insufficient_context",
                    isFallbackIDontKnow = true
                )
            ),
            fallbackAnswerText = "Не знаю на основе найденного контекста. Уточни вопрос или сформулируй его иначе.\n\nИсточники: нет — недостаточно релевантного контекста."
        )
        val baseMessages = listOf(
            Message(MessageRole.SYSTEM, "system"),
            Message(MessageRole.USER, userInput)
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns activeMessages
        coEvery { chatSettingsRepository.getSettings() } returns ContextStrategyConfig(ContextStrategyType.SLIDING_WINDOW)
        every { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { contextStrategy.buildContext(null, any(), any()) } returns baseMessages
        every { agent.buildRequestConfigWithProfile(any()) } returns RequestConfig(systemPrompt = "system")

        val result = handler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = "user-1",
            mcpContext = null,
            answerMode = AnswerMode.RAG_ENHANCED,
            preparedRagRequest = preparedRagRequest
        ) as ChatMessageResult.Success

        assertEquals(
            "Не знаю на основе найденного контекста. Уточни вопрос или сформулируй его иначе.",
            result.aiResponse
        )
        assertFalse(result.aiResponse.contains("Источники:"))
        assertEquals(
            "Не знаю на основе найденного контекста. Уточни вопрос или сформулируй его иначе.",
            result.retrievalSummary?.groundedAnswer?.answerText
        )
    }
}
