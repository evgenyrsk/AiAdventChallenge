package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.chat.ChatMessageResult
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.TaskStateRepository
import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import com.example.aiadventchallenge.rag.memory.TaskStateUpdater
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessChatTurnUseCaseTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val branchRepository = mockk<BranchRepository>(relaxed = true)
    private val taskStateRepository = mockk<TaskStateRepository>(relaxed = true)
    private val taskStateUpdater = mockk<TaskStateUpdater>()
    private val chatMessageHandler = mockk<ChatMessageHandler>()
    private val prepareRagRequestUseCase = mockk<PrepareRagRequestUseCase>()

    private val useCase = ProcessChatTurnUseCase(
        chatRepository = chatRepository,
        branchRepository = branchRepository,
        taskStateRepository = taskStateRepository,
        taskStateUpdater = taskStateUpdater,
        chatMessageHandler = chatMessageHandler,
        prepareRagRequestUseCase = prepareRagRequestUseCase
    )

    @Test
    fun `enhanced turn updates task state and uses prepared rag request`() = runTest {
        val userMessage = ChatMessage("user-1", null, "Хочу похудеть", true, branchId = "main")
        val aiMessage = ChatMessage("ai-1", "user-1", "Ответ", false, branchId = "main")
        val taskState = ConversationTaskState(
            dialogGoal = "Снижение веса с устойчивыми привычками",
            latestSummary = "Пользователь хочет похудеть"
        )
        val retrievalSummary = RetrievalSummary(
            query = "Хочу похудеть",
            originalQuery = "Хочу похудеть",
            effectiveQuery = "Хочу похудеть",
            source = "fitness_knowledge",
            strategy = "structure_aware",
            selectedCount = 1,
            topKBeforeFilter = 4,
            finalTopK = 2,
            contextEnvelope = "Envelope",
            chunks = emptyList()
        )
        val prepared = PreparedRagRequest(
            systemPromptSuffix = "RAG MODE",
            userPrompt = "Question",
            retrievalSummary = retrievalSummary
        )

        coEvery { chatRepository.getMessagesByBranch("main") } returns emptyList()
        coEvery { taskStateRepository.getTaskState("main") } returns null
        every { taskStateUpdater.update(any(), any(), any()) } returns taskState
        coEvery { chatMessageHandler.saveUserMessage(any(), any(), any()) } returns userMessage
        coEvery { prepareRagRequestUseCase.invoke(any(), any(), any(), any()) } returns prepared
        coEvery {
            chatMessageHandler.generateAiResponse(
                userInput = any(),
                fitnessProfile = any(),
                activeBranchId = any(),
                parentMessageId = any(),
                mcpContext = any(),
                answerMode = any(),
                preparedRagRequest = prepared
            )
        } returns ChatMessageResult.Success(
            userMessage = userMessage,
            aiMessage = aiMessage,
            aiResponse = "Ответ",
            retrievalSummary = retrievalSummary
        )

        val result = useCase(
            userInput = "Хочу похудеть",
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = "main",
            parentMessageId = null,
            mcpContext = null,
            answerMode = AnswerMode.RAG_ENHANCED
        )

        assertEquals("Снижение веса с устойчивыми привычками", result.taskState?.dialogGoal)
        assertEquals("fitness_knowledge", result.retrievalSummary?.source)
        assertTrue(result.result is ChatMessageResult.Success)
        coVerify { taskStateRepository.upsertTaskState("main", taskState) }
        coVerify { prepareRagRequestUseCase.invoke(any(), any(), any(), any()) }
    }
}
