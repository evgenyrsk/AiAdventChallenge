package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class McpToolOrchestratorTest {

    private lateinit var callMcpToolUseCase: CallMcpToolUseCase
    private lateinit var nutritionRequestDetector: NutritionRequestDetector
    private lateinit var fitnessRequestDetector: FitnessRequestDetector
    private lateinit var orchestrator: McpToolOrchestratorImpl

    @Before
    fun setup() {
        callMcpToolUseCase = mockk(relaxed = true)
        nutritionRequestDetector = mockk(relaxed = true)
        fitnessRequestDetector = mockk(relaxed = true)
        orchestrator = McpToolOrchestratorImpl(
            callMcpToolUseCase = callMcpToolUseCase,
            nutritionRequestDetector = nutritionRequestDetector,
            fitnessRequestDetector = fitnessRequestDetector
        )
    }
    
    @Test
    fun `detect nutrition request - should return success with context`() = runTest {
        val userInput = "Рассчитай калории для мужчины 30 лет"
        
        coEvery { nutritionRequestDetector.detectParams(any()) } returns CalculateNutritionParams(
            sex = "male",
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = "moderate",
            goal = "maintenance"
        )
        
        coEvery { callMcpToolUseCase(any(), any()) } returns McpToolData.StringResult("Calories: 2500")
        
        val result = orchestrator.detectAndExecuteTool(userInput)
        
        assertTrue(result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success
        
        assertNotNull(successResult.context)
        assertTrue(successResult.context.contains("2500"))
        assertTrue(successResult.context.contains("РАСЧЕТ ПИТАНИЯ"))
        
        coVerify(exactly = 1) { nutritionRequestDetector.detectParams(userInput) }
        coVerify(exactly = 1) {
            callMcpToolUseCase(
                name = "calculate_nutrition_plan",
                params = mapOf(
                    "sex" to "male",
                    "age" to 30,
                    "heightCm" to 180.0,
                    "weightKg" to 80.0,
                    "activityLevel" to "moderate",
                    "goal" to "maintenance"
                )
            )
        }
    }
    
    @Test
    fun `no nutrition request detected - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"
        
        coEvery { nutritionRequestDetector.detectParams(any()) } returns null
        
        val result = orchestrator.detectAndExecuteTool(userInput)
        
        assertEquals(ToolExecutionResult.NoToolFound, result)
        
        coVerify(exactly = 1) { nutritionRequestDetector.detectParams(userInput) }
        coVerify(exactly = 0) { callMcpToolUseCase(any(), any()) }
    }
    
    @Test
    fun `MCP tool call fails - should return error`() = runTest {
        val userInput = "Рассчитай калории"
        
        coEvery { nutritionRequestDetector.detectParams(any()) } returns CalculateNutritionParams(
            sex = "male",
            age = 30,
            heightCm = 180.0,
            weightKg = 80.0,
            activityLevel = "moderate",
            goal = "maintenance"
        )
        
        coEvery { callMcpToolUseCase(any(), any()) } throws Exception("Network error")
        
        val result = orchestrator.detectAndExecuteTool(userInput)
        
        assertTrue(result is ToolExecutionResult.Error)
        val errorResult = result as ToolExecutionResult.Error
        
        assertEquals("Network error", errorResult.message)
    }
    
    @Test
    fun `empty input - should return NoToolFound`() = runTest {
        val userInput = ""
        
        coEvery { nutritionRequestDetector.detectParams(any()) } returns null
        
        val result = orchestrator.detectAndExecuteTool(userInput)
        
        assertEquals(ToolExecutionResult.NoToolFound, result)
    }
}
