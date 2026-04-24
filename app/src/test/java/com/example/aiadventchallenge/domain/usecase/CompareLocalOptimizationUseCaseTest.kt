package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.repository.LocalOllamaRepository
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareLocalOptimizationUseCaseTest {

    private val prepareRagRequestUseCase = mockk<PrepareRagRequestUseCase>()
    private val chatAgent = mockk<ChatAgent>()
    private val localOllamaRepository = mockk<LocalOllamaRepository>()
    private val chatSettingsRepository = mockk<ChatSettingsRepository>()

    private val useCase = CompareLocalOptimizationUseCase(
        prepareRagRequestUseCase = prepareRagRequestUseCase,
        chatAgent = chatAgent,
        localOllamaRepository = localOllamaRepository,
        chatSettingsRepository = chatSettingsRepository,
        localLlmProfileResolver = LocalLlmProfileResolver()
    )

    @Test
    fun `comparison runs baseline and optimized profiles separately`() = runTest {
        val question = "Почему сон влияет на восстановление?"
        val prepared = PreparedRagRequest(
            systemPromptSuffix = "RAG MODE",
            userPrompt = "Question with retrieved context",
            retrievalSummary = RetrievalSummary(
                query = question,
                originalQuery = question,
                effectiveQuery = question,
                source = "fitness_knowledge",
                strategy = "structure_aware",
                selectedCount = 2,
                topKBeforeFilter = 6,
                finalTopK = 4,
                contextEnvelope = "Envelope",
                chunks = emptyList()
            ),
            retrievalLatencyMs = 20L
        )
        val config = RequestConfig(systemPrompt = "Base system")
        val capturedMessages = mutableListOf<List<Message>>()

        coEvery { prepareRagRequestUseCase.invoke(question, any(), any(), any(), any()) } returns prepared
        every { chatAgent.buildRequestConfigWithProfile(FitnessProfileType.INTERMEDIATE) } returns config
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.LOCAL_OLLAMA,
            localConfig = LocalLlmConfig(model = "qwen2.5:3b-instruct")
        )
        coEvery {
            localOllamaRepository.askWithContext(
                messages = capture(capturedMessages),
                config = any(),
                requestType = any(),
                backendSettings = any()
            )
        } returnsMany listOf(
            ChatResult.Success(
                AnswerWithUsage(
                    content = "baseline answer",
                    promptTokens = 10,
                    completionTokens = 20,
                    totalTokens = 30
                )
            ),
            ChatResult.Success(
                AnswerWithUsage(
                    content = "optimized answer",
                    promptTokens = 11,
                    completionTokens = 18,
                    totalTokens = 29
                )
            )
        )

        val result = useCase(
            question = question,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            answerMode = AnswerMode.RAG_ENHANCED
        )

        assertEquals(question, result.question)
        assertTrue(result.baselineRun.success)
        assertTrue(result.optimizedRun.success)
        assertEquals("Question with retrieved context", capturedMessages.first().last().content)
        assertEquals("Question with retrieved context", capturedMessages.last().last().content)
        assertTrue(result.optimizedRun.latencyMs >= result.optimizedRun.generationLatencyMs ?: 0L)
        coVerify(exactly = 2) { localOllamaRepository.askWithContext(any(), any(), any(), any()) }
    }
}
