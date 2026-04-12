package com.example.aiadventchallenge.domain.chat

import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.data.config.TaskIntent
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.context.ContextStrategy
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatMessageHandlerTest {
    
    private lateinit var chatRepository: ChatRepository
    private lateinit var agent: ChatAgent
    private lateinit var contextStrategyFactory: ContextStrategyFactory
    private lateinit var chatSettingsRepository: ChatSettingsRepository
    private lateinit var contextStrategy: ContextStrategy
    private lateinit var handler: ChatMessageHandlerImpl
    
    private val testTask = TaskContext(
        taskId = "test-task",
        query = "Create workout plan",
        phase = TaskPhase.PLANNING,
        currentStep = 1,
        totalSteps = 3,
        profile = FitnessProfileType.INTERMEDIATE,
        isActive = true,
        awaitingUserConfirmation = false
    )
    
    @Before
    fun setup() {
        chatRepository = mockk(relaxed = true)
        agent = mockk()
        contextStrategy = mockk(relaxed = true)
        contextStrategyFactory = mockk()
        chatSettingsRepository = mockk(relaxed = true)
        
        handler = ChatMessageHandlerImpl(
            chatRepository = chatRepository,
            agent = agent,
            contextStrategyFactory = contextStrategyFactory,
            chatSettingsRepository = chatSettingsRepository
        )
    }
    
    @Test
    fun `handle successful user message`() = runTest {
        val userInput = "Create a workout plan"
        val activeBranchId = "main"
        val parentMessageId = null
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Processing",
            result = "Here is your workout plan: ...",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null,
            planReady = true
        )
        
        val answerWithUsage = AnswerWithUsage(
            content = aiResponse.toString(),
            promptTokens = 100,
            completionTokens = 200,
            totalTokens = 300
        )
        
        coEvery { chatSettingsRepository.getSettings() } returns mockk(relaxed = true)
        coEvery { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { chatRepository.getMessagesByBranch(activeBranchId) } returns emptyList()
        coEvery { agent.buildRequestConfigWithTask(any(), any(), any()) } returns RequestConfig(
            systemPrompt = "Test prompt"
        )
        coEvery { contextStrategy.buildContext(any(), any(), any()) } returns emptyList()
        coEvery { agent.processRequestWithContextAndUsage(any(), any(), any(), any()) } returns ChatResult.Success(answerWithUsage)
        
        val result = handler.handleUserMessage(
            userInput = userInput,
            taskContext = null,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        assertTrue(result is ChatMessageResult.Success)
        val successResult = result as ChatMessageResult.Success
        
        assertNotNull(successResult.userMessage)
        assertNotNull(successResult.aiMessage)
        assertEquals(userInput, successResult.userMessage.content)
        assertTrue(successResult.aiMessage.content.isNotEmpty())
        
        coVerify(exactly = 1) { chatRepository.insertMessage(successResult.userMessage, activeBranchId, parentMessageId) }
        coVerify(exactly = 1) { chatRepository.insertMessage(successResult.aiMessage, activeBranchId, successResult.aiMessage.parentMessageId) }
    }
    
    @Test
    fun `handle empty AI response`() = runTest {
        val userInput = "Test question"
        val activeBranchId = "main"
        val parentMessageId = null
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Processing",
            result = "",  // Пустой ответ
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val answerWithUsage = AnswerWithUsage(
            content = aiResponse.toString(),
            promptTokens = 100,
            completionTokens = 0,
            totalTokens = 100
        )
        
        coEvery { chatSettingsRepository.getSettings() } returns mockk(relaxed = true)
        coEvery { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { chatRepository.getMessagesByBranch(activeBranchId) } returns emptyList()
        coEvery { agent.buildRequestConfigWithTask(any(), any(), any()) } returns RequestConfig(
            systemPrompt = "Test prompt"
        )
        coEvery { contextStrategy.buildContext(any(), any(), any()) } returns emptyList()
        coEvery { agent.processRequestWithContextAndUsage(any(), any(), any(), any()) } returns ChatResult.Success(answerWithUsage)
        
        val result = handler.handleUserMessage(
            userInput = userInput,
            taskContext = null,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        assertTrue(result is ChatMessageResult.EmptyResponse)
        val emptyResponse = result as ChatMessageResult.EmptyResponse
        
        assertNotNull(emptyResponse.userMessage)
        assertEquals(userInput, emptyResponse.userMessage.content)
        assertTrue(emptyResponse.errorMessage.contains("Не удалось получить ответ"))
    }
    
    @Test
    fun `handle LLM error`() = runTest {
        val userInput = "Test question"
        val activeBranchId = "main"
        val parentMessageId = null
        val errorMessage = "Network error"
        
        coEvery { chatSettingsRepository.getSettings() } returns mockk(relaxed = true)
        coEvery { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { chatRepository.getMessagesByBranch(activeBranchId) } returns emptyList()
        coEvery { agent.buildRequestConfigWithTask(any(), any(), any()) } returns RequestConfig(
            systemPrompt = "Test prompt"
        )
        coEvery { contextStrategy.buildContext(any(), any(), any()) } returns emptyList()
        coEvery { agent.processRequestWithContextAndUsage(any(), any(), any(), any()) } returns ChatResult.Error(
            message = errorMessage,
            code = 500
        )
        
        val result = handler.handleUserMessage(
            userInput = userInput,
            taskContext = null,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        assertTrue(result is ChatMessageResult.Error)
        val errorResult = result as ChatMessageResult.Error
        
        assertNotNull(errorResult.userMessage)
        assertEquals(userInput, errorResult.userMessage.content)
        assertEquals(errorMessage, errorResult.errorMessage)
    }
    
    @Test
    fun `user message is created correctly`() = runTest {
        val userInput = "Hello"
        val activeBranchId = "main"
        val parentMessageId = "msg-123"
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Processing",
            result = "Hi there!",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val answerWithUsage = AnswerWithUsage(
            content = aiResponse.toString(),
            promptTokens = 50,
            completionTokens = 30,
            totalTokens = 80
        )
        
        coEvery { chatSettingsRepository.getSettings() } returns mockk(relaxed = true)
        coEvery { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { chatRepository.getMessagesByBranch(activeBranchId) } returns emptyList()
        coEvery { agent.buildRequestConfigWithTask(any(), any(), any()) } returns RequestConfig(
            systemPrompt = "Test prompt"
        )
        coEvery { contextStrategy.buildContext(any(), any(), any()) } returns emptyList()
        coEvery { agent.processRequestWithContextAndUsage(any(), any(), any(), any()) } returns ChatResult.Success(answerWithUsage)
        
        val result = handler.handleUserMessage(
            userInput = userInput,
            taskContext = null,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        val successResult = result as ChatMessageResult.Success
        
        assertEquals(userInput, successResult.userMessage.content)
        assertEquals(activeBranchId, successResult.userMessage.branchId)
        assertEquals(parentMessageId, successResult.userMessage.parentMessageId)
        assertTrue(successResult.userMessage.isFromUser)
    }
    
    @Test
    fun `AI message is created correctly`() = runTest {
        val userInput = "Hello"
        val activeBranchId = "main"
        val parentMessageId = null
        
        val aiResponse = EnhancedTaskAiResponse(
            taskIntent = TaskIntent.CONTINUE_TASK,
            newTaskQuery = null,
            stepCompleted = false,
            nextAction = "Processing",
            result = "Hi there!",
            transitionTo = null,
            pauseTask = false,
            needClarification = null,
            errorMessage = null
        )
        
        val answerWithUsage = AnswerWithUsage(
            content = aiResponse.toString(),
            promptTokens = 50,
            completionTokens = 30,
            totalTokens = 80
        )
        
        coEvery { chatSettingsRepository.getSettings() } returns mockk(relaxed = true)
        coEvery { contextStrategyFactory.create(any()) } returns contextStrategy
        coEvery { chatRepository.getMessagesByBranch(activeBranchId) } returns emptyList()
        coEvery { agent.buildRequestConfigWithTask(any(), any(), any()) } returns RequestConfig(
            systemPrompt = "Test prompt"
        )
        coEvery { contextStrategy.buildContext(any(), any(), any()) } returns emptyList()
        coEvery { agent.processRequestWithContextAndUsage(any(), any(), any(), any()) } returns ChatResult.Success(answerWithUsage)
        
        val result = handler.handleUserMessage(
            userInput = userInput,
            taskContext = null,
            fitnessProfile = FitnessProfileType.INTERMEDIATE,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        val successResult = result as ChatMessageResult.Success
        
        assertEquals(aiResponse.result, successResult.aiMessage.content)
        assertEquals(activeBranchId, successResult.aiMessage.branchId)
        assertEquals(successResult.userMessage.id, successResult.aiMessage.parentMessageId)
        assertFalse(successResult.aiMessage.isFromUser)
        assertEquals(50, successResult.aiMessage.promptTokens)
        assertEquals(30, successResult.aiMessage.completionTokens)
        assertEquals(80, successResult.aiMessage.totalTokens)
    }
}
