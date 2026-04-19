package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.model.mcp.Exercise
import com.example.aiadventchallenge.domain.model.mcp.MealGuidanceResponse
import com.example.aiadventchallenge.domain.model.mcp.MealSuggestion
import com.example.aiadventchallenge.domain.model.mcp.NutritionMetricsResponse
import com.example.aiadventchallenge.domain.model.mcp.TrainingDay
import com.example.aiadventchallenge.domain.model.mcp.TrainingGuidanceResponse
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FitnessFlowDetectionTest {

    private lateinit var callMcpToolUseCase: CallMcpToolUseCase
    private lateinit var orchestrator: McpToolOrchestratorImpl

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        callMcpToolUseCase = mockk(relaxed = true)
        coEvery { callMcpToolUseCase(any(), any()) } answers {
            when (invocation.args[0] as String) {
                "calculate_nutrition_metrics" -> McpToolData.NutritionMetrics(
                    NutritionMetricsResponse(
                        bmr = 1600,
                        tdee = 2100,
                        targetCalories = 1800,
                        proteinG = 130,
                        fatG = 60,
                        carbsG = 180,
                        notes = "default"
                    )
                )
                "generate_meal_guidance" -> McpToolData.MealGuidance(
                    MealGuidanceResponse(
                        mealStrategy = "default",
                        mealDistribution = listOf(MealSuggestion(1, 600, 40, "Meal")),
                        recommendedFoods = listOf("protein"),
                        foodsToLimit = listOf("sugar"),
                        notes = "default"
                    )
                )
                "generate_training_guidance" -> McpToolData.TrainingGuidance(
                    TrainingGuidanceResponse(
                        trainingSplit = "3x/week",
                        weeklyPlan = listOf(
                            TrainingDay(1, "Full Body", listOf(Exercise("Squat", 3, "8-10")))
                        ),
                        exercisePrinciples = "consistency",
                        recoveryNotes = "sleep",
                        notes = "default"
                    )
                )
                else -> error("Unexpected tool")
            }
        }
        orchestrator = McpToolOrchestratorImpl(callMcpToolUseCase)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
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
        val userInput = "Составь фитнес-сводку моих тренировок"

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
        val userInput = "Рассчитай калории для мужчины 30 лет"

        coEvery { callMcpToolUseCase(any(), any()) } throws Exception("Connection failed")

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Error)
        val errorResult = result as ToolExecutionResult.Error
        assertEquals("Connection failed", errorResult.message)
    }
}
