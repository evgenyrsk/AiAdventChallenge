package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ChatResult
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AiMemoryClassifierTest {

    @Mock
    private lateinit var aiRepository: com.example.aiadventchallenge.domain.repository.AiRepository

    private lateinit var classifier: AiMemoryClassifier

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        classifier = AiMemoryClassifier(aiRepository)
    }

    @Test
    fun `test classify task goal with LLM`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Хочу составить план обучения",
            isFromUser = true,
            branchId = "main"
        )

        // Мокируем успешный LLM-ответ с JSON
        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """{"action": "create", "memoryType": "working", "reason": "TASK_GOAL", "importance": 0.9, "value": "составить план обучения", "key": "task_goal", "existingKeyToUpdate": null}""",
                promptTokens = 50,
                completionTokens = 30,
                totalTokens = 80
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, metrics) = result.value
        assertTrue(classificationResult is ClassificationResult.Create)
        assertEquals(MemoryType.WORKING, (classificationResult as ClassificationResult.Create).memoryType)
        assertEquals(MemoryReason.TASK_GOAL, (classificationResult as ClassificationResult.Create).reason)
        assertEquals(50, metrics.promptTokens)
        assertEquals(30, metrics.completionTokens)
        assertEquals(80, metrics.totalTokens)
        assertTrue(metrics.executionTimeMs > 0)
    }

    @Test
    fun `test classify skip filler with LLM`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Ок, понял",
            isFromUser = true,
            branchId = "main"
        )

        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """{"action": "skip", "memoryType": null, "reason": null, "importance": null, "value": null, "key": null, "existingKeyToUpdate": null}""",
                promptTokens = 40,
                completionTokens = 20,
                totalTokens = 60
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, _) = result.value
        assertEquals(ClassificationResult.Skip, classificationResult)
    }

    @Test
    fun `test classify user name with LLM`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Меня зовут Иван",
            isFromUser = true,
            branchId = "main"
        )

        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """{"action": "create", "memoryType": "long_term", "reason": "USER_NAME", "importance": 0.9, "value": "Иван", "key": "user_name", "existingKeyToUpdate": null}""",
                promptTokens = 45,
                completionTokens = 25,
                totalTokens = 70
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, _) = result.value
        assertTrue(classificationResult is ClassificationResult.Create)
        assertEquals(MemoryType.LONG_TERM, (classificationResult as ClassificationResult.Create).memoryType)
        assertEquals(MemoryReason.USER_NAME, (classificationResult as ClassificationResult.Create).reason)
        assertEquals("Иван", (classificationResult as ClassificationResult.Create).value)
    }

    @Test
    fun `test handle LLM error gracefully`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Test message",
            isFromUser = true,
            branchId = "main"
        )

        // Мокируем ошибку LLM
        whenever(aiRepository.askWithUsage(any(), any(), any(), any()))
            .thenReturn(ChatResult.Error("API error"))

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `test parse malformed JSON gracefully`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Test message",
            isFromUser = true,
            branchId = "main"
        )

        // Мокируем некорректный JSON
        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = "This is not a valid JSON",
                promptTokens = 40,
                completionTokens = 10,
                totalTokens = 50
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, _) = result.value
        assertEquals(ClassificationResult.Skip, classificationResult) // fallback to Skip
    }

    @Test
    fun `test parse JSON with markdown code block`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Хочу изучить Android",
            isFromUser = true,
            branchId = "main"
        )

        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """```json
{"action": "create", "memoryType": "working", "reason": "TASK_GOAL", "importance": 0.9, "value": "изучить Android", "key": "task_goal", "existingKeyToUpdate": null}
```""",
                promptTokens = 40,
                completionTokens = 25,
                totalTokens = 65
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, _) = result.value
        assertTrue(classificationResult is ClassificationResult.Create)
    }

    @Test
    fun `test parse JSON with multiline code block`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Мне 32 года",
            isFromUser = true,
            branchId = "main"
        )

        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """```json
{
    "action": "create",
    "memoryType": "long_term",
    "reason": "USER_PROFILE_DATA",
    "importance": 0.8,
    "value": "32 года",
    "key": "user_age",
    "existingKeyToUpdate": null
}
```""",
                promptTokens = 40,
                completionTokens = 30,
                totalTokens = 70
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, _) = result.value
        assertTrue(classificationResult is ClassificationResult.Create)
        assertEquals("32 года", (classificationResult as ClassificationResult.Create).value)
    }

    @Test
    fun `test parse JSON with javascript code block`() = kotlinx.coroutines.test.runTest {
        // Arrange
        val message = ChatMessage(
            id = "1",
            parentMessageId = null,
            content = "Хочу изучить Android",
            isFromUser = true,
            branchId = "main"
        )

        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """```javascript
{"action": "create", "memoryType": "working", "reason": "TASK_GOAL", "importance": 0.9, "value": "изучить Android", "key": "task_goal", "existingKeyToUpdate": null}
```""",
                promptTokens = 40,
                completionTokens = 25,
                totalTokens = 65
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyUserMessage(message, emptyList(), emptyList())

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResult, _) = result.value
        assertTrue(classificationResult is ClassificationResult.Create)
    }

    @Test
    fun `test classify assistant message with LLM`() = kotlinx.coroutines.test.runTest {
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

        val mockResponse = ChatResult.Success(
            AnswerWithUsage(
                content = """{"action": "create", "memoryType": "long_term", "reason": "CONFIRMED_FACT", "importance": 0.9, "value": "Android разработка", "key": "confirmed_topic", "existingKeyToUpdate": "topic"}""",
                promptTokens = 50,
                completionTokens = 30,
                totalTokens = 80
            )
        )
        whenever(aiRepository.askWithUsage(any(), any(), any(), any())).thenReturn(mockResponse)

        // Act
        val result = classifier.classifyAssistantMessage(message, workingMemory)

        // Assert
        assertTrue(result.isSuccess)
        val (classificationResults, metrics) = result.value.first()
        assertTrue(classificationResults.isNotEmpty())
        val firstResult = classificationResults[0] as ClassificationResult.Create
        assertEquals(MemoryType.LONG_TERM, firstResult.memoryType)
        assertEquals(MemoryReason.CONFIRMED_FACT, firstResult.reason)
        assertEquals(50, metrics.promptTokens)
    }
}