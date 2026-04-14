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
            systemPromptSuffix = "RAG MODE\nне додумывай факты",
            userPrompt = "Вопрос пользователя:\n$userInput\n\nRetrieved Context:\nКонтекст",
            retrievalSummary = RetrievalSummary(
                query = userInput,
                source = "fitness_knowledge",
                strategy = "structure_aware",
                selectedCount = 1,
                contextEnvelope = "Envelope",
                chunks = listOf(
                    RetrievalSourceCard(
                        title = "recovery_sleep_steps.md",
                        relativePath = "training/recovery_sleep_steps.md",
                        section = "Why sleep matters",
                        score = 0.92
                    )
                )
            )
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns activeMessages
        coEvery { chatSettingsRepository.getSettings() } returns ContextStrategyConfig(ContextStrategyType.SLIDING_WINDOW)
        every { contextStrategyFactory.create(any()) } returns contextStrategy
        every { agent.buildRequestConfigWithProfile(any()) } returns RequestConfig(systemPrompt = "system")
        coEvery { contextStrategy.buildContext(null, activeMessages, any()) } returns baseMessages
        coEvery { prepareRagRequestUseCase.invoke(any(), any(), any(), any(), any(), any(), any()) } returns preparedRagRequest

        val capturedMessages = mutableListOf<List<Message>>()
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
            answerMode = AnswerMode.RAG
        ) as ChatMessageResult.Success

        coVerify(exactly = 1) {
            prepareRagRequestUseCase.invoke(any(), any(), any(), any(), any(), any(), any())
        }

        assertEquals(userInput, capturedMessages[0].last().content)
        assertTrue(capturedMessages[1].last().content.contains("Retrieved Context"))
        assertEquals("fitness_knowledge", ragResult.retrievalSummary?.source)
        assertEquals(null, plainResult.retrievalSummary)
    }
}
