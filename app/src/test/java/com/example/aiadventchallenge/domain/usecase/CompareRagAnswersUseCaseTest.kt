package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.repository.LocalOllamaRepository
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.repository.AiRepository
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

class CompareRagAnswersUseCaseTest {

    private val prepareRagRequestUseCase = mockk<PrepareRagRequestUseCase>()
    private val chatAgent = mockk<ChatAgent>()
    private val remoteRepository = mockk<AiRepository>()
    private val localOllamaRepository = mockk<LocalOllamaRepository>()
    private val chatSettingsRepository = mockk<ChatSettingsRepository>()

    private val useCase = CompareRagAnswersUseCase(
        prepareRagRequestUseCase = prepareRagRequestUseCase,
        chatAgent = chatAgent,
        remoteRepository = remoteRepository,
        localOllamaRepository = localOllamaRepository,
        chatSettingsRepository = chatSettingsRepository
    )

    @Test
    fun `comparison uses one prepared rag request for both backends`() = runTest {
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
            )
        )
        val config = RequestConfig(systemPrompt = "Base system")
        val capturedRemoteMessages = mutableListOf<List<Message>>()
        val capturedLocalMessages = mutableListOf<List<Message>>()

        coEvery { prepareRagRequestUseCase.invoke(question, any(), any(), any()) } returns prepared
        every { chatAgent.buildRequestConfigWithProfile(FitnessProfileType.INTERMEDIATE) } returns config
        coEvery { chatSettingsRepository.getAiBackendSettings() } returns AiBackendSettings(
            selectedBackend = AiBackendType.LOCAL_OLLAMA
        )
        coEvery {
            localOllamaRepository.askWithContext(
                messages = capture(capturedLocalMessages),
                config = any(),
                requestType = any(),
                backendSettings = any()
            )
        } returns ChatResult.Success(
            AnswerWithUsage("local answer", promptTokens = 10, completionTokens = 20, totalTokens = 30)
        )
        coEvery {
            remoteRepository.askWithContext(
                messages = capture(capturedRemoteMessages),
                config = any(),
                requestType = any()
            )
        } returns ChatResult.Success(
            AnswerWithUsage("cloud answer", promptTokens = 11, completionTokens = 21, totalTokens = 32)
        )

        val result = useCase(
            question = question,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            answerMode = AnswerMode.RAG_ENHANCED
        )

        assertEquals(question, result.question)
        assertTrue(result.localRun.success)
        assertTrue(result.cloudRun.success)
        assertEquals("Question with retrieved context", capturedLocalMessages.single().last().content)
        assertEquals("Question with retrieved context", capturedRemoteMessages.single().last().content)
        assertEquals(MessageRole.SYSTEM.value, capturedLocalMessages.single().first().role)
        coVerify(exactly = 1) { prepareRagRequestUseCase.invoke(question, any(), any(), any()) }
    }
}
