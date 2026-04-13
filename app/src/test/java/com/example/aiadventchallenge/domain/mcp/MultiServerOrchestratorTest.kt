package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.mcp.McpToolData.*
import com.example.aiadventchallenge.domain.model.mcp.*
import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MultiServerOrchestratorTest {

    private lateinit var multiServerRepository: MultiServerRepository
    private lateinit var orchestrator: MultiServerOrchestrator

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        multiServerRepository = mockk(relaxed = true)
        orchestrator = MultiServerOrchestrator(multiServerRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `detect and execute tools - nutrition to meal to training`() = runTest {
        val userInput = "Рассчитай калории, составь план питания и тренировок для мужчины 30 лет, рост 175 см, вес 75 кг для похудения"

        val nutritionResponse = NutritionMetricsResponse(
            bmr = 1720,
            tdee = 2150,
            targetCalories = 1750,
            proteinG = 140,
            fatG = 58,
            carbsG = 180,
            notes = "Дефицит 400 ккал"
        )

        val mealResponse = MealGuidanceResponse(
            mealStrategy = "High protein deficit",
            mealDistribution = listOf(
                MealSuggestion(1, 600, 45, "Oatmeal")
            ),
            recommendedFoods = listOf("chicken", "salmon"),
            foodsToLimit = listOf("sweets"),
            notes = "Focus on protein"
        )

        val trainingResponse = TrainingGuidanceResponse(
            trainingSplit = "Full Body 3x/week",
            weeklyPlan = listOf(
                TrainingDay(1, "Full Body", listOf(
                    Exercise("Squat", 3, "12-15")
                ))
            ),
            exercisePrinciples = "Progressive overload",
            recoveryNotes = "Sleep 7-8 hours",
            notes = "Combine with deficit"
        )

        coEvery {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        } returns NutritionMetrics(nutritionResponse)

        coEvery {
            multiServerRepository.callTool(eq("generate_meal_guidance"), match { params ->
                params["targetCalories"] == 1750 && params["proteinG"] == 140
            })
        } returns MealGuidance(mealResponse)

        coEvery {
            multiServerRepository.callTool(eq("generate_training_guidance"), any())
        } returns TrainingGuidance(trainingResponse)

        val result = orchestrator.detectAndExecuteTools(userInput)

        assertTrue("Should be Success", result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success

        assertTrue("Should contain NUTRITION METRICS",
            successResult.context.contains("NUTRITION METRICS"))
        assertTrue("Should contain MEAL GUIDANCE",
            successResult.context.contains("MEAL GUIDANCE"))
        assertTrue("Should contain TRAINING GUIDANCE",
            successResult.context.contains("TRAINING GUIDANCE"))

        assertTrue("Should contain server IDs",
            successResult.context.contains("nutrition-metrics-server-1"))
        assertTrue("Should contain server IDs",
            successResult.context.contains("meal-guidance-server-1"))
        assertTrue("Should contain server IDs",
            successResult.context.contains("training-guidance-server-1"))

        coVerifyOrder {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
            multiServerRepository.callTool(eq("generate_meal_guidance"), any())
            multiServerRepository.callTool(eq("generate_training_guidance"), any())
        }
    }

    @Test
    fun `error handling - server down should not break other servers`() = runTest {
        val userInput = "Рассчитай калории для мужчины 30 лет"

        coEvery {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        } returns NutritionMetrics(NutritionMetricsResponse(1720, 2150, 2150, 160, 75, 250, "Maintenance"))

        coEvery {
            multiServerRepository.callTool(eq("generate_meal_guidance"), any())
        } throws Exception("Server down")

        val result = orchestrator.detectAndExecuteTools(userInput)

        assertTrue("Should be Error", result is ToolExecutionResult.Error)
        val errorResult = result as ToolExecutionResult.Error
        assertEquals("Server down", errorResult.message)

        coVerify(exactly = 1) {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        }
        coVerify(atMost = 1) {
            multiServerRepository.callTool(eq("generate_meal_guidance"), any())
        }
    }

    @Test
    fun `correct tool selection - only nutrition requested`() = runTest {
        val userInput = "Рассчитай мои калории"

        coEvery {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        } returns NutritionMetrics(NutritionMetricsResponse(1720, 2150, 2150, 160, 75, 250, "Maintenance"))

        orchestrator.detectAndExecuteTools(userInput)

        coVerify(exactly = 1) {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        }
        coVerify(exactly = 0) {
            multiServerRepository.callTool(eq("generate_meal_guidance"), any())
        }
        coVerify(exactly = 0) {
            multiServerRepository.callTool(eq("generate_training_guidance"), any())
        }
    }

    @Test
    fun `no fitness request - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"

        val result = orchestrator.detectAndExecuteTools(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)

        coVerify(exactly = 0) {
            multiServerRepository.callTool(any(), any())
        }
    }
}
