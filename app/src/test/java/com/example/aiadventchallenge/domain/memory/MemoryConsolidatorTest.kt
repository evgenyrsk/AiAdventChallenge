package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics
import com.example.aiadventchallenge.domain.model.MemoryConfig
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MemoryConsolidatorTest {

    @Mock
    private lateinit var aiClassifier: AiMemoryClassifier

    private lateinit var config: MemoryConfig
    private lateinit var consolidator: MemoryConsolidator

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        config = MemoryConfig(
            shortTermWindow = 10,
            workingMemoryTTL = 30 * 60 * 1000,
            longTermImportanceThreshold = 0.7f
        )
        consolidator = MemoryConsolidator(aiClassifier, config)
    }

    @Test
    fun `test consolidateConversationPair returns working and long-term updates`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Хочу создать Android приложение",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Отлично! Давайте создадим Android приложение.",
            isFromUser = false,
            branchId = "main"
        )

        val userResult = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.TASK_GOAL,
            importance = 0.9f,
            value = "создать Android приложение",
            source = MemorySource.USER_EXTRACTED,
            key = "task_goal"
        )

        val assistantResult = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.ACTIVE_ENTITY,
            importance = 0.8f,
            value = "Android",
            source = MemorySource.ASSISTANT_CONFIRMED,
            key = "platform"
        )

        val pairResult = ConversationPairMultiClassification(
            userResults = listOf(userResult),
            assistantResults = listOf(assistantResult),
            newTaskDetected = false,
            metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                promptTokens = 50,
                completionTokens = 30,
                totalTokens = 80,
                executionTimeMs = 150
            )
        )

        whenever(aiClassifier.classifyConversationPair(any(), any(), any(), any()))
            .thenReturn(kotlin.Result.success(pairResult))

        // Act
        val result = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            emptyList(),
            emptyList()
        )

        // Assert
        assertEquals(2, result.workingMemoryUpdates.size)
        assertEquals(0, result.longTermUpdates.size)
        assertEquals(0, result.skippedCount)
        assertFalse(result.newTaskDetected)
        assertEquals(1, result.metrics.userClassifications)
        assertEquals(1, result.metrics.assistantClassifications)
        assertEquals(2, result.metrics.workingUpdates)
        assertEquals(0, result.metrics.longTermUpdates)
    }

    @Test
    fun `test consolidateConversationPair filters low importance long-term entries`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Некоторая информация",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Понял.",
            isFromUser = false,
            branchId = "main"
        )

        val lowImportanceResult = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.TASK_PARAMETER,
            importance = 0.5f,
            value = "информация",
            source = MemorySource.USER_EXTRACTED,
            key = "info"
        )

        val pairResult = ConversationPairMultiClassification(
            userResults = listOf(lowImportanceResult),
            assistantResults = emptyList(),
            newTaskDetected = false,
            metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                promptTokens = 40,
                completionTokens = 20,
                totalTokens = 60,
                executionTimeMs = 100
            )
        )

        whenever(aiClassifier.classifyConversationPair(any(), any(), any(), any()))
            .thenReturn(kotlin.Result.success(pairResult))

        // Act
        val result = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            emptyList(),
            emptyList()
        )

        // Assert
        assertEquals(0, result.workingMemoryUpdates.size)
        assertEquals(0, result.longTermUpdates.size)
        assertEquals(1, result.skippedCount)
        assertFalse(result.newTaskDetected)
    }

    @Test
    fun `test consolidateConversationPair handles empty classifications`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Привет",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Привет!",
            isFromUser = false,
            branchId = "main"
        )

        val pairResult = ConversationPairMultiClassification(
            userResults = emptyList(),
            assistantResults = emptyList(),
            newTaskDetected = false,
            metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                promptTokens = 30,
                completionTokens = 10,
                totalTokens = 40,
                executionTimeMs = 80
            )
        )

        whenever(aiClassifier.classifyConversationPair(any(), any(), any(), any()))
            .thenReturn(kotlin.Result.success(pairResult))

        // Act
        val result = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            emptyList(),
            emptyList()
        )

        // Assert
        assertEquals(0, result.workingMemoryUpdates.size)
        assertEquals(0, result.longTermUpdates.size)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `test consolidateConversationPair handles classification failure`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Тест",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Ответ",
            isFromUser = false,
            branchId = "main"
        )

        whenever(aiClassifier.classifyConversationPair(any(), any(), any(), any()))
            .thenReturn(kotlin.Result.failure(Exception("LLM error")))

        // Act
        val result = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            emptyList(),
            emptyList()
        )

        // Assert
        assertEquals(0, result.workingMemoryUpdates.size)
        assertEquals(0, result.longTermUpdates.size)
        assertEquals(0, result.skippedCount)
        assertFalse(result.newTaskDetected)
        assertEquals(0, result.metrics.userClassifications)
        assertEquals(0, result.metrics.assistantClassifications)
        assertEquals(0, result.metrics.workingUpdates)
        assertEquals(0, result.metrics.longTermUpdates)
    }

    @Test
    fun `test consolidateConversationPair handles mixed working and long-term results`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Меня зовут Иван, я хочу изучить Kotlin",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Отлично, Иван! Kotlin - отличный язык.",
            isFromUser = false,
            branchId = "main"
        )

        val userNameResult = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.USER_NAME,
            importance = 0.9f,
            value = "Иван",
            source = MemorySource.USER_EXTRACTED,
            key = "user_name"
        )

        val taskGoalResult = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.TASK_GOAL,
            importance = 0.9f,
            value = "изучить Kotlin",
            source = MemorySource.USER_EXTRACTED,
            key = "task_goal"
        )

        val activeEntityResult = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.ACTIVE_ENTITY,
            importance = 0.8f,
            value = "Kotlin",
            source = MemorySource.ASSISTANT_CONFIRMED,
            key = "language"
        )

        val pairResult = ConversationPairMultiClassification(
            userResults = listOf(userNameResult, taskGoalResult),
            assistantResults = listOf(activeEntityResult),
            newTaskDetected = false,
            metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                promptTokens = 60,
                completionTokens = 40,
                totalTokens = 100,
                executionTimeMs = 200
            )
        )

        whenever(aiClassifier.classifyConversationPair(any(), any(), any(), any()))
            .thenReturn(kotlin.Result.success(pairResult))

        // Act
        val result = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            emptyList(),
            emptyList()
        )

        // Assert
        assertEquals(2, result.workingMemoryUpdates.size)
        assertEquals(1, result.longTermUpdates.size)
        assertEquals(0, result.skippedCount)
        assertFalse(result.newTaskDetected)
        assertEquals(2, result.metrics.userClassifications)
        assertEquals(1, result.metrics.assistantClassifications)
        assertEquals(2, result.metrics.workingUpdates)
        assertEquals(1, result.metrics.longTermUpdates)
    }

    @Test
    fun `test consolidateConversationPair handles new task detected`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Теперь другая задача",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Понял, какая новая задача?",
            isFromUser = false,
            branchId = "main"
        )

        val pairResult = ConversationPairMultiClassification(
            userResults = emptyList(),
            assistantResults = emptyList(),
            newTaskDetected = true,
            metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                promptTokens = 30,
                completionTokens = 10,
                totalTokens = 40,
                executionTimeMs = 80
            )
        )

        whenever(aiClassifier.classifyConversationPair(any(), any(), any(), any()))
            .thenReturn(kotlin.Result.success(pairResult))

        // Act
        val result = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            emptyList(),
            emptyList()
        )

        // Assert
        assertEquals(0, result.workingMemoryUpdates.size)
        assertEquals(0, result.longTermUpdates.size)
        assertEquals(0, result.skippedCount)
        assertTrue(result.newTaskDetected)
    }
}