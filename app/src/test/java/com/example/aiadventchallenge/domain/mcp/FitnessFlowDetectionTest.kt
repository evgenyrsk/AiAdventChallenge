package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FitnessFlowDetectionTest {

    private lateinit var callMcpToolUseCase: CallMcpToolUseCase
    private lateinit var orchestrator: McpToolOrchestratorImpl

    @Before
    fun setup() {
        callMcpToolUseCase = mockk(relaxed = true)
        orchestrator = McpToolOrchestratorImpl(callMcpToolUseCase)
    }

    @Test
    fun `detect fitness flow request - should return success`() = runTest {
        val userInput = "Найди последние фитнес логи за неделю, составь сводку и создай напоминание"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success
        assertTrue(successResult.context.contains("FITNESS MCP FLOW"))
    }

    @Test
    fun `detect fitness keyword - should trigger flow`() = runTest {
        val userInput = "Покажи мои фитнес логи за месяц"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun `detect training keyword - should trigger flow`() = runTest {
        val userInput = "Какие тренировки были на прошлой неделе?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun `detect workout keyword - should trigger flow`() = runTest {
        val userInput = "My workout summary for this week"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun `detect summary keyword - should trigger flow`() = runTest {
        val userInput = "Составь сводку моих тренировок"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun `detect reminder keyword - should trigger flow`() = runTest {
        val userInput = "Напомни мне про тренировку завтра"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
    }

    @Test
    fun `no fitness request - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)
    }

    @Test
    fun `general question - should return NoToolFound`() = runTest {
        val userInput = "Что ты умеешь делать?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)
    }

    @Test
    fun `empty input - should return NoToolFound`() = runTest {
        val userInput = ""

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)
    }

    @Test
    fun `fitness flow execution fails - should return error`() = runTest {
        val userInput = "Покажи мои фитнес логи"

        coEvery { callMcpToolUseCase.executeMultiServerFlow(any()) } throws Exception("Connection failed")

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Error)
        val errorResult = result as ToolExecutionResult.Error
        assertEquals("Connection failed", errorResult.message)
    }
}
