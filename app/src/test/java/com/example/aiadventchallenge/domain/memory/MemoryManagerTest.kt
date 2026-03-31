package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity
import com.example.aiadventchallenge.data.local.dao.MemoryClassificationDao
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.MemoryConfig
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MemoryManagerTest {

    @Mock
    private lateinit var memoryRepository: MemoryRepository

    @Mock
    private lateinit var chatRepository: ChatRepository

    @Mock
    private lateinit var aiClassifier: AiMemoryClassifier

    @Mock
    private lateinit var classificationRepository: MemoryClassificationRepository

    private lateinit var config: MemoryConfig
    private lateinit var memoryManager: MemoryManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        config = MemoryConfig(
            shortTermWindow = 10,
            workingMemoryTTL = 30 * 60 * 1000,
            longTermImportanceThreshold = 0.7f
        )
        memoryManager = MemoryManager(
            memoryRepository,
            chatRepository,
            aiClassifier,
            config,
            classificationRepository
        )
    }

    @Test
    fun `test onUserMessage saves when classification succeeds`() = runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Хочу составить план обучения",
            isFromUser = true,
            branchId = "main"
        )
        val metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
            promptTokens = 50,
            completionTokens = 30,
            totalTokens = 80,
            executionTimeMs = 150
        )
        val createResult = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.TASK_GOAL,
            importance = 0.9f,
            value = "составить план обучения",
            source = MemorySource.USER_EXTRACTED,
            key = "task_goal"
        )
        val classificationResult = kotlinx.coroutines.Pair(createResult, metrics)

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(aiClassifier.classifyUserMessage(any(), any(), any()))
            .thenReturn(kotlin.coroutines.Result.success(classificationResult))
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onUserMessage(message)

        // Assert
        verify(memoryRepository).deactivateExpiredEntries()
        verify(memoryRepository).insertEntry(any())
        verify(classificationRepository).saveClassificationMetrics(any())
    }

    @Test
    fun `test onUserMessage skips when classification is Skip`() = runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Ок, понял",
            isFromUser = true,
            branchId = "main"
        )
        val metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
            promptTokens = 40,
            completionTokens = 20,
            totalTokens = 60,
            executionTimeMs = 100
        )
        val skipResult = kotlinx.coroutines.Pair(ClassificationResult.Skip, metrics)

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(aiClassifier.classifyUserMessage(any(), any(), any()))
            .thenReturn(kotlin.coroutines.Result.success(skipResult))
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onUserMessage(message)

        // Assert
        verify(memoryRepository, never()).insertEntry(any())
        verify(classificationRepository).saveClassificationMetrics(any())
    }

    @Test
    fun `test onUserMessage does not save when classification fails`() = runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Test message",
            isFromUser = true,
            branchId = "main"
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(aiClassifier.classifyUserMessage(any(), any(), any()))
            .thenReturn(kotlin.coroutines.Result.failure(Exception("LLM error")))

        // Act
        memoryManager.onUserMessage(message)

        // Assert - НЕ сохраняем при ошибке
        verify(memoryRepository, never()).insertEntry(any())
        verify(classificationRepository, never()).saveClassificationMetrics(any())
    }

    @Test
    fun `test onUserMessage saves to long-term with sufficient importance`() = runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Меня зовут Иван",
            isFromUser = true,
            branchId = "main"
        )
        val metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
            promptTokens = 45,
            completionTokens = 25,
            totalTokens = 70,
            executionTimeMs = 120
        )
        val createResult = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.USER_NAME,
            importance = 0.9f,
            value = "Иван",
            source = MemorySource.USER_EXTRACTED,
            key = "user_name"
        )
        val classificationResult = kotlinx.coroutines.Pair(createResult, metrics)

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getEntryByKey(any(), any())).thenReturn(null)
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(aiClassifier.classifyUserMessage(any(), any(), any()))
            .thenReturn(kotlin.coroutines.Result.success(classificationResult))
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onUserMessage(message)

        // Assert
        verify(memoryRepository).insertEntry(any())
    }

    @Test
    fun `test onUserMessage skips long-term with insufficient importance`() = runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Какая-то информация",
            isFromUser = true,
            branchId = "main"
        )
        val metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
            promptTokens = 40,
            completionTokens = 20,
            totalTokens = 60,
            executionTimeMs = 100
        )
        val createResult = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.TASK_PARAMETER,
            importance = 0.5f, // ниже порога 0.7
            value = "информация",
            source = MemorySource.USER_EXTRACTED,
            key = "info"
        )
        val classificationResult = kotlinx.coroutines.Pair(createResult, metrics)

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(aiClassifier.classifyUserMessage(any(), any(), any()))
            .thenReturn(kotlin.coroutines.Result.success(classificationResult))
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onUserMessage(message)

        // Assert
        verify(memoryRepository, never()).insertEntry(any())
    }

    @Test
    fun `test onAssistantMessage promotes to long-term memory`() = runTest {
        // Arrange
        val workingMemory = listOf(
            MemoryEntry(
                id = "w1",
                key = "topic",
                value = "Android разработка",
                memoryType = MemoryType.WORKING,
                reason = MemoryReason.TASK_PARAMETER,
                source = MemorySource.USER_EXTRACTED,
                importance = 0.8f,
                branchId = "main",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        val message = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Отлично! Android разработка - хорошая тема",
            isFromUser = false,
            branchId = "main"
        )
        val metrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
            promptTokens = 50,
            completionTokens = 30,
            totalTokens = 80,
            executionTimeMs = 150
        )
        val createResult = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.CONFIRMED_FACT,
            importance = 0.9f,
            value = "Android разработка",
            source = MemorySource.ASSISTANT_CONFIRMED,
            key = "confirmed_topic"
        )
        val classificationResult = kotlinx.coroutines.Pair(createResult, metrics)

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(workingMemory))
        whenever(memoryRepository.getEntryByKey(any(), any())).thenReturn(null)
        whenever(aiClassifier.classifyAssistantMessage(any(), any()))
            .thenReturn(kotlin.coroutines.Result.success(listOf(classificationResult)))
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onAssistantMessage(message)

        // Assert
        verify(memoryRepository).insertEntry(any())
    }

    @Test
    fun `test getShortTermMessages takes last N messages`() = runTest {
        // Arrange
        val messages = (1..20).map { i ->
            ChatMessage(
                id = "$i",
                parentMessageId = if (i > 1) "${i-1}" else null,
                content = "Message $i",
                isFromUser = i % 2 == 1,
                branchId = "main"
            )
        }

        // Act
        val shortTerm = memoryManager.getShortTermMessages(messages)

        // Assert
        assertEquals(10, shortTerm.size)
        assertEquals("11", shortTerm.first().id)
        assertEquals("20", shortTerm.last().id)
    }

    @Test
    fun `test buildMemoryContext returns correct structure`() = runTest {
        // Arrange
        val workingMemory = listOf(
            MemoryEntry(
                id = "w1",
                key = "goal",
                value = "составить план обучения",
                memoryType = MemoryType.WORKING,
                reason = MemoryReason.TASK_GOAL,
                source = MemorySource.USER_EXTRACTED,
                importance = 0.9f,
                branchId = "main",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        val longTermMemory = listOf(
            MemoryEntry(
                id = "l1",
                key = "user_name",
                value = "Иван",
                memoryType = MemoryType.LONG_TERM,
                reason = MemoryReason.USER_NAME,
                source = MemorySource.USER_EXTRACTED,
                importance = 0.9f,
                branchId = "main",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(workingMemory))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(longTermMemory))

        // Act
        val context = memoryManager.buildMemoryContext("main")

        // Assert
        assertNotNull(context)
        assertEquals(1, context.workingMemory.size)
        assertEquals(1, context.longTermMemory.size)
        assertEquals("goal", context.workingMemory[0].key)
        assertEquals("user_name", context.longTermMemory[0].key)
    }

    @Test
    fun `test clearTask clears working memory and classifications`() = runTest {
        // Act
        memoryManager.clearTask("main")

        // Assert
        verify(memoryRepository).clearWorkingMemory("main")
        verify(classificationRepository).clearClassifications("main")
    }

    @Test
    fun `test clearProfile clears long-term memory`() = runTest {
        // Act
        memoryManager.clearProfile("main")

        // Assert
        verify(memoryRepository).clearLongTermMemory("main")
    }

    @Test
    fun `test clearAll clears all memory and classifications`() = runTest {
        // Act
        memoryManager.clearAll("main")

        // Assert
        verify(memoryRepository).clearAllMemory("main")
        verify(classificationRepository).clearClassifications("main")
    }
}