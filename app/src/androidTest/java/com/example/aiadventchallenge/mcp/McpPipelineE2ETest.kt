package com.example.aiadventchallenge.mcp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aiadventchallenge.domain.mcp.McpToolData
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestratorImpl
import com.example.aiadventchallenge.domain.mcp.ToolExecutionResult
import com.example.aiadventchallenge.domain.model.mcp.*
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
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
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class McpPipelineE2ETest {

    private lateinit var callMcpToolUseCaseMock: CallMcpToolUseCase
    private lateinit var orchestrator: McpToolOrchestratorImpl

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        callMcpToolUseCaseMock = mockk(relaxed = true)
        orchestrator = McpToolOrchestratorImpl(
            callMcpToolUseCase = callMcpToolUseCaseMock
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scenario 1 weight loss - verify orchestrator execution`() = runTest {
        val userInput = "Рассчитай калории, составь план питания и тренировок для мужчины 30 лет, рост 175 см, вес 75 кг для похудения"

        val nutritionResponse = NutritionMetricsResponse(
            bmr = 1720, tdee = 2150, targetCalories = 1750,
            proteinG = 140, fatG = 58, carbsG = 180,
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
            callMcpToolUseCaseMock("calculate_nutrition_metrics", match { params ->
                params["sex"] == "male" && params["age"] == 30 &&
                params["heightCm"] == 175 && params["weightKg"] == 75.0 &&
                params["goal"] == "weight_loss"
            })
        } returns McpToolData.NutritionMetrics(nutritionResponse)

        coEvery {
            callMcpToolUseCaseMock("generate_meal_guidance", match { params ->
                params["targetCalories"] == 1750 && params["proteinG"] == 140
            })
        } returns McpToolData.MealGuidance(mealResponse)

        coEvery {
            callMcpToolUseCaseMock("generate_training_guidance", match { params ->
                params["goal"] == "weight_loss"
            })
        } returns McpToolData.TrainingGuidance(trainingResponse)

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue("Should be Success", result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success

        assertTrue("Should contain NUTRITION METRICS",
            successResult.context.contains("NUTRITION METRICS"))
        assertTrue("Should contain MEAL GUIDANCE",
            successResult.context.contains("MEAL GUIDANCE"))
        assertTrue("Should contain TRAINING GUIDANCE",
            successResult.context.contains("TRAINING GUIDANCE"))

        assertTrue("Should pass targetCalories",
            successResult.context.contains("Target Calories: 1750"))
        assertTrue("Should pass proteinG",
            successResult.context.contains("Protein: 140г"))

        coVerifyOrder {
            callMcpToolUseCaseMock("calculate_nutrition_metrics", any())
            callMcpToolUseCaseMock("generate_meal_guidance", any())
            callMcpToolUseCaseMock("generate_training_guidance", any())
        }
    }

    @Test
    fun `scenario 2 muscle gain - verify correct parameters`() = runTest {
        val userInput = "Нужна программа питания и тренировок для набора массы"

        val nutritionResponse = NutritionMetricsResponse(
            bmr = 1720, tdee = 2150, targetCalories = 2300,
            proteinG = 180, fatG = 75, carbsG = 220,
            notes = "Профицит 150 ккал"
        )

        coEvery {
            callMcpToolUseCaseMock("calculate_nutrition_metrics", match { params ->
                params["goal"] == "muscle_gain"
            })
        } returns McpToolData.NutritionMetrics(nutritionResponse)

        coEvery {
            callMcpToolUseCaseMock("generate_meal_guidance", match { params ->
                params["targetCalories"] == 2300 && params["proteinG"] == 180
            })
        } returns McpToolData.MealGuidance(MealGuidanceResponse(
            mealStrategy = "High protein surplus",
            mealDistribution = listOf(MealSuggestion(1, 800, 60, "Oatmeal")),
            recommendedFoods = listOf("beef"),
            foodsToLimit = listOf("sweets"),
            notes = "Surplus 150-300 kcal"
        ))

        coEvery {
            callMcpToolUseCaseMock("generate_training_guidance", any())
        } returns McpToolData.TrainingGuidance(TrainingGuidanceResponse(
            trainingSplit = "Push/Pull/Legs",
            weeklyPlan = listOf(TrainingDay(1, "Push", listOf(Exercise("Bench", 4, "8-10")))),
            exercisePrinciples = "Hypertrophy focus",
            recoveryNotes = "Sleep 8-9 hours",
            notes = "Combine with surplus"
        ))

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success

        assertTrue("Should contain higher calories for muscle gain",
            successResult.context.contains("Target Calories: 2300"))
        assertTrue("Should contain higher protein for muscle gain",
            successResult.context.contains("Protein: 180г"))

        assertTrue("Should contain surplus",
            successResult.context.contains("surplus"))
    }

    @Test
    fun `error handling - tool failure stops pipeline`() = runTest {
        val userInput = "Рассчитай калории для мужчины 30 лет"

        coEvery {
            callMcpToolUseCaseMock("calculate_nutrition_metrics", any())
        } throws Exception("Connection failed")

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue("Should be Error", result is ToolExecutionResult.Error)
        val errorResult = result as ToolExecutionResult.Error
        assertEquals("Connection failed", errorResult.message)

        coVerify(exactly = 0) {
            callMcpToolUseCaseMock("generate_meal_guidance", any())
        }
    }

    @Test
    fun `no fitness request - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)

        coVerify(exactly = 0) {
            callMcpToolUseCaseMock(any(), any())
        }
    }
}
