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
    private lateinit var consolidator: MemoryConsolidator

    @Mock
    private lateinit var classificationRepository: MemoryClassificationRepository

    private lateinit var memoryManager: MemoryManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        memoryManager = MemoryManager(
            memoryRepository,
            chatRepository,
            consolidator,
            classificationRepository
        )
    }

    @Test
    fun `test onConversationPair saves working memory entries`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Хочу составить план обучения",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Отлично! Давайте составим план.",
            isFromUser = false,
            branchId = "main"
        )
        
        val workingUpdate = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.TASK_GOAL,
            importance = 0.9f,
            value = "составить план обучения",
            source = MemorySource.USER_EXTRACTED,
            key = "task_goal"
        )
        
        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = listOf(workingUpdate),
            longTermUpdates = emptyList(),
            skippedCount = 0,
            newTaskDetected = false,
            metrics = ConsolidationMetrics(
                userClassifications = 1,
                assistantClassifications = 0,
                workingUpdates = 1,
                longTermUpdates = 0,
                skippedCount = 0,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 50,
                    completionTokens = 30,
                    totalTokens = 80,
                    executionTimeMs = 150
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)
        whenever(memoryRepository.getEntryByKey(any(), any())).thenReturn(null)
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository).deactivateExpiredEntries()
        verify(memoryRepository).insertEntry(any())
        verify(classificationRepository).saveClassificationMetrics(any())
    }

    @Test
    fun `test onConversationPair saves long-term entries`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Меня зовут Иван",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Приятно познакомиться, Иван!",
            isFromUser = false,
            branchId = "main"
        )
        
        val longTermUpdate = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.USER_NAME,
            importance = 0.9f,
            value = "Иван",
            source = MemorySource.USER_EXTRACTED,
            key = "user_name"
        )

        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = emptyList(),
            longTermUpdates = listOf(longTermUpdate),
            skippedCount = 0,
            newTaskDetected = false,
            metrics = ConsolidationMetrics(
                userClassifications = 1,
                assistantClassifications = 0,
                workingUpdates = 0,
                longTermUpdates = 1,
                skippedCount = 0,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 45,
                    completionTokens = 25,
                    totalTokens = 70,
                    executionTimeMs = 120
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)
        whenever(memoryRepository.getEntryByKey(any(), any())).thenReturn(null)
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository).insertEntry(any())
        verify(classificationRepository).saveClassificationMetrics(any())
    }

    @Test
    fun `test onConversationPair skips existing long-term entries`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Меня зовут Иван",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Приятно познакомиться, Иван!",
            isFromUser = false,
            branchId = "main"
        )
        
        val longTermUpdate = ClassificationResult.Create(
            memoryType = MemoryType.LONG_TERM,
            reason = MemoryReason.USER_NAME,
            importance = 0.9f,
            value = "Иван",
            source = MemorySource.USER_EXTRACTED,
            key = "user_name"
        )
        
        val existingEntry = MemoryEntry(
            id = "existing",
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

        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = emptyList(),
            longTermUpdates = listOf(longTermUpdate),
            skippedCount = 0,
            newTaskDetected = false,
            metrics = ConsolidationMetrics(
                userClassifications = 1,
                assistantClassifications = 0,
                workingUpdates = 0,
                longTermUpdates = 1,
                skippedCount = 0,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 45,
                    completionTokens = 25,
                    totalTokens = 70,
                    executionTimeMs = 120
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)
        whenever(memoryRepository.getEntryByKey("user_name", "main")).thenReturn(existingEntry)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository, never()).insertEntry(any())
    }

    @Test
    fun `test onConversationPair skips when no updates`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Ок, понял",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Хорошо!",
            isFromUser = false,
            branchId = "main"
        )

        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = emptyList(),
            longTermUpdates = emptyList(),
            skippedCount = 2,
            newTaskDetected = false,
            metrics = ConsolidationMetrics(
                userClassifications = 0,
                assistantClassifications = 0,
                workingUpdates = 0,
                longTermUpdates = 0,
                skippedCount = 2,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 40,
                    completionTokens = 20,
                    totalTokens = 60,
                    executionTimeMs = 100
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository, never()).insertEntry(any())
        verify(classificationRepository, never()).saveClassificationMetrics(any())
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

    @Test
    fun `test onConversationPair clears working memory when new task detected`() = runTest {
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

        val existingWorkingMemory = listOf(
            MemoryEntry(
                id = "existing",
                key = "old_goal",
                value = "старая задача",
                memoryType = MemoryType.WORKING,
                reason = MemoryReason.TASK_GOAL,
                source = MemorySource.USER_EXTRACTED,
                importance = 0.9f,
                branchId = "main",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = emptyList(),
            longTermUpdates = emptyList(),
            skippedCount = 0,
            newTaskDetected = true,
            metrics = ConsolidationMetrics(
                userClassifications = 0,
                assistantClassifications = 0,
                workingUpdates = 0,
                longTermUpdates = 0,
                skippedCount = 0,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 30,
                    completionTokens = 10,
                    totalTokens = 40,
                    executionTimeMs = 80
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(existingWorkingMemory))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository).clearWorkingMemory("main")
    }

    @Test
    fun `test onConversationPair preserves working memory when same task`() = runTest {
        // Arrange
        val userMessage = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "А если добавить параметр?",
            isFromUser = true,
            branchId = "main"
        )
        val assistantMessage = ChatMessage(
            id = "2",
            parentMessageId = "1",
            content = "Хорошо, добавим параметр",
            isFromUser = false,
            branchId = "main"
        )

        val existingWorkingMemory = listOf(
            MemoryEntry(
                id = "existing",
                key = "old_goal",
                value = "старая задача",
                memoryType = MemoryType.WORKING,
                reason = MemoryReason.TASK_GOAL,
                source = MemorySource.USER_EXTRACTED,
                importance = 0.9f,
                branchId = "main",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )

        val newParameter = ClassificationResult.Create(
            memoryType = MemoryType.WORKING,
            reason = MemoryReason.TASK_PARAMETER,
            importance = 0.8f,
            value = "параметр",
            source = MemorySource.USER_EXTRACTED,
            key = "param"
        )

        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = listOf(newParameter),
            longTermUpdates = emptyList(),
            skippedCount = 0,
            newTaskDetected = false,
            metrics = ConsolidationMetrics(
                userClassifications = 1,
                assistantClassifications = 0,
                workingUpdates = 1,
                longTermUpdates = 0,
                skippedCount = 0,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 40,
                    completionTokens = 20,
                    totalTokens = 60,
                    executionTimeMs = 100
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(existingWorkingMemory))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)
        whenever(memoryRepository.getEntryByKey(any(), any())).thenReturn(null)
        whenever(classificationRepository.saveClassificationMetrics(any())).thenReturn(Unit)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository, never()).clearWorkingMemory("main")
        verify(memoryRepository).insertEntry(any())
    }

    @Test
    fun `test onConversationPair preserves long-term memory on new task`() = runTest {
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

        val existingLongTermMemory = listOf(
            MemoryEntry(
                id = "existing",
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

        val consolidationResult = ConsolidationResult(
            workingMemoryUpdates = emptyList(),
            longTermUpdates = emptyList(),
            skippedCount = 0,
            newTaskDetected = true,
            metrics = ConsolidationMetrics(
                userClassifications = 0,
                assistantClassifications = 0,
                workingUpdates = 0,
                longTermUpdates = 0,
                skippedCount = 0,
                llmMetrics = com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics(
                    promptTokens = 30,
                    completionTokens = 10,
                    totalTokens = 40,
                    executionTimeMs = 80
                )
            )
        )

        whenever(memoryRepository.getWorkingMemory("main")).thenReturn(flowOf(emptyList()))
        whenever(memoryRepository.getLongTermMemory("main")).thenReturn(flowOf(existingLongTermMemory))
        whenever(memoryRepository.deactivateExpiredEntries()).thenReturn(Unit)
        whenever(consolidator.consolidateConversationPair(any(), any(), any(), any()))
            .thenReturn(consolidationResult)

        // Act
        memoryManager.onConversationPair(userMessage, assistantMessage)

        // Assert
        verify(memoryRepository).clearWorkingMemory("main")
        verify(memoryRepository, never()).clearLongTermMemory("main")
    }
}