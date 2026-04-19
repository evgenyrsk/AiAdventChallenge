package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.mcp.McpToolData.*
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class McpOrchestratorPipelineTest {

    private lateinit var callMcpToolUseCase: CallMcpToolUseCase
    private lateinit var orchestrator: McpToolOrchestratorImpl
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        callMcpToolUseCase = mockk(relaxed = true)
        orchestrator = McpToolOrchestratorImpl(callMcpToolUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scenario 1 weight loss - verify full pipeline execution`() = runTest {
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
                MealSuggestion(1, 600, 45, "Oatmeal"),
                MealSuggestion(2, 450, 35, "Chicken"),
                MealSuggestion(3, 700, 60, "Salmon")
            ),
            recommendedFoods = listOf("chicken", "salmon"),
            foodsToLimit = listOf("sweets"),
            notes = "Focus on protein"
        )

        val trainingResponse = TrainingGuidanceResponse(
            trainingSplit = "Full Body 3x/week",
            weeklyPlan = listOf(
                TrainingDay(1, "Full Body", listOf(
                    Exercise("Squat", 3, "12-15"),
                    Exercise("Bench", 3, "12-15")
                ))
            ),
            exercisePrinciples = "Progressive overload",
            recoveryNotes = "Sleep 7-8 hours",
            notes = "Combine with deficit"
        )

        coEvery { callMcpToolUseCase("calculate_nutrition_metrics", any()) } returns NutritionMetrics(nutritionResponse)
        coEvery { callMcpToolUseCase("generate_meal_guidance", any()) } returns MealGuidance(mealResponse)
        coEvery { callMcpToolUseCase("generate_training_guidance", any()) } returns TrainingGuidance(trainingResponse)

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue("Should be Success", result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success

        assertTrue("Should contain NUTRITION METRICS",
            successResult.context.contains("NUTRITION METRICS"))
        assertTrue("Should contain MEAL GUIDANCE",
            successResult.context.contains("MEAL GUIDANCE"))
        assertTrue("Should pass targetCalories",
            successResult.context.contains("Target Calories: 1750"))
        assertTrue("Should pass proteinG",
            successResult.context.contains("Protein: 140г"))
        assertTrue("Should pass fatG",
            successResult.context.contains("Fat: 58г"))
        assertTrue("Should pass carbsG",
            successResult.context.contains("Carbs: 180г"))

        coVerifyOrder {
            callMcpToolUseCase("calculate_nutrition_metrics", any())
            callMcpToolUseCase("generate_meal_guidance", any())
        }

        coVerify(exactly = 1) {
            callMcpToolUseCase("calculate_nutrition_metrics", any())
        }
        coVerify(exactly = 1) {
            callMcpToolUseCase("generate_meal_guidance", any())
        }
    }

    @Test
    fun `scenario 2 muscle gain - verify correct parameters`() = runTest {
        val userInput = "Нужна программа питания и тренировок для набора массы"

        val nutritionResponse = NutritionMetricsResponse(
            bmr = 1720,
            tdee = 2150,
            targetCalories = 2300,
            proteinG = 180,
            fatG = 75,
            carbsG = 220,
            notes = "Профицит 150 ккал"
        )

        coEvery {
            callMcpToolUseCase("calculate_nutrition_metrics", match { params ->
                params["goal"] == "muscle_gain"
            })
        } returns NutritionMetrics(nutritionResponse)

        coEvery {
            callMcpToolUseCase("generate_meal_guidance", match { params ->
                params["targetCalories"] == 2300 &&
                params["proteinG"] == 180
            })
        } returns MealGuidance(MealGuidanceResponse(
            mealStrategy = "High protein surplus",
            mealDistribution = listOf(
                MealSuggestion(1, 800, 60, "Oatmeal"),
                MealSuggestion(2, 600, 50, "Beef"),
                MealSuggestion(3, 900, 70, "Chicken")
            ),
            recommendedFoods = listOf("beef", "salmon"),
            foodsToLimit = listOf("sweets"),
            notes = "Surplus 150-300 kcal"
        ))

        coEvery {
            callMcpToolUseCase("generate_training_guidance", match { params ->
                params["goal"] == "muscle_gain"
            })
        } returns TrainingGuidance(TrainingGuidanceResponse(
            trainingSplit = "Push/Pull/Legs 4x/week",
            weeklyPlan = listOf(
                TrainingDay(1, "Push", listOf(
                    Exercise("Bench Press", 4, "8-10")
                ))
            ),
            exercisePrinciples = "Hypertrophy focus",
            recoveryNotes = "Sleep 8-9 hours",
            notes = "Combine with surplus"
        ))

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success

        assertTrue("Should contain targetCalories for muscle gain",
            successResult.context.contains("Target Calories: 2300"))
        assertTrue("Should contain higher protein for muscle gain",
            successResult.context.contains("Protein: 180г"))
    }

    @Test
    fun `error handling - nutrition tool failure stops pipeline`() = runTest {
        val userInput = "Рассчитай калории для мужчины 30 лет"

        coEvery {
            callMcpToolUseCase("calculate_nutrition_metrics", any())
        } throws Exception("Connection failed")

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue("Should be Error", result is ToolExecutionResult.Error)
        val errorResult = result as ToolExecutionResult.Error
        assertEquals("Connection failed", errorResult.message)

        coVerify(exactly = 0) {
            callMcpToolUseCase("generate_meal_guidance", any())
        }
        coVerify(exactly = 0) {
            callMcpToolUseCase("generate_training_guidance", any())
        }
    }

    @Test
    fun `default values applied for missing parameters`() = runTest {
        val userInput = "Рассчитай калории"

        coEvery {
            callMcpToolUseCase("calculate_nutrition_metrics", match { params ->
                params["sex"] == "male" &&
                params["age"] == 30 &&
                params["heightCm"] == 175 &&
                params["weightKg"] == 70.0 &&
                params["activityLevel"] == "moderate" &&
                params["goal"] == "maintenance"
            })
        } returns NutritionMetrics(NutritionMetricsResponse(
            bmr = 1720,
            tdee = 2150,
            targetCalories = 2150,
            proteinG = 160,
            fatG = 75,
            carbsG = 250,
            notes = "Maintenance"
        ))

        orchestrator.detectAndExecuteTool(userInput)

        coVerify(exactly = 1) {
            callMcpToolUseCase("calculate_nutrition_metrics", any())
        }
    }

    @Test
    fun `intent extraction - correct tools selected for mixed request`() = runTest {
        val userInput = "Мне нужно питание и тренировки"

        coEvery {
            callMcpToolUseCase(any(), any())
        } returns NutritionMetrics(NutritionMetricsResponse(
            bmr = 1720,
            tdee = 2150,
            targetCalories = 2150,
            proteinG = 160,
            fatG = 75,
            carbsG = 250,
            notes = "Maintenance"
        ))

        orchestrator.detectAndExecuteTool(userInput)

        coVerify(exactly = 1) {
            callMcpToolUseCase("calculate_nutrition_metrics", any())
        }
        coVerify(atLeast = 1) {
            callMcpToolUseCase("generate_meal_guidance", any())
        }
        coVerify(atLeast = 1) {
            callMcpToolUseCase("generate_training_guidance", any())
        }
    }

    @Test
    fun `no fitness request - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)
    }

    @Test
    fun `empty input - should return NoToolFound`() = runTest {
        val userInput = ""

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)
    }
}
