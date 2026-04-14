package com.example.aiadventchallenge.domain.mcp

import com.example.aiadventchallenge.domain.mcp.McpToolData.*
import com.example.aiadventchallenge.domain.model.mcp.*
import com.example.aiadventchallenge.data.mcp.MultiServerRepository
import io.mockk.*
import android.util.Log
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
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        multiServerRepository = mockk(relaxed = true)
        orchestrator = MultiServerOrchestrator(multiServerRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `detect and execute tools - nutrition to meal to training`() = runTest {
        val userInput = "Рассчитай калории, составь план питания и план тренировки для мужчины 30 лет, рост 175 см, вес 75 кг для похудения"

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

        val result = orchestrator.detectAndExecuteTool(userInput)

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
        val userInput = "Рассчитай калории и составь план питания для мужчины 30 лет рост 175 см вес 75 кг"

        coEvery {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        } returns NutritionMetrics(NutritionMetricsResponse(1720, 2150, 2150, 160, 75, 250, "Maintenance"))

        coEvery {
            multiServerRepository.callTool(eq("generate_meal_guidance"), any())
        } throws Exception("Server down")

        val result = orchestrator.detectAndExecuteTool(userInput)

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
        val userInput = "Рассчитай мои калории для мужчины 30 лет рост 180 см вес 75 кг"

        coEvery {
            multiServerRepository.callTool(eq("calculate_nutrition_metrics"), any())
        } returns NutritionMetrics(NutritionMetricsResponse(1720, 2150, 2150, 160, 75, 250, "Maintenance"))

        orchestrator.detectAndExecuteTool(userInput)

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
    fun `general weight loss question should not trigger nutrition metrics`() = runTest {
        val userInput = "Что важнее для похудения: дефицит калорий или время приёма пищи?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)

        coVerify(exactly = 0) {
            multiServerRepository.callTool(any(), any())
        }
    }

    @Test
    fun `general protein question should not trigger nutrition metrics`() = runTest {
        val userInput = "Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)

        coVerify(exactly = 0) {
            multiServerRepository.callTool(any(), any())
        }
    }

    @Test
    fun `no fitness request - should return NoToolFound`() = runTest {
        val userInput = "Привет, как дела?"

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertEquals(ToolExecutionResult.NoToolFound, result)

        coVerify(exactly = 0) {
            multiServerRepository.callTool(any(), any())
        }
    }

    @Test
    fun `knowledge request - should route to answer_with_retrieval`() = runTest {
        val userInput = "Объясни по документации проекта как устроен task state machine и retrieval"
        val retrievalPayload = """
            {
              "message": "Prepared answer package with retrieval for local_docs",
              "data": {
                "query": "Объясни по документации проекта как устроен task state machine и retrieval",
                "retrievalApplied": true,
                "retrieval": {
                  "source": "local_docs",
                  "strategy": "structure_aware",
                  "selectedCount": 1,
                  "contextEnvelope": "Use the following retrieved project knowledge when answering.\n\n[TASK_STATE_MACHINE.md | TaskContext | score=0.500]\n### TaskContext",
                  "chunks": [
                    {
                      "title": "TASK_STATE_MACHINE.md",
                      "relativePath": "docs/TASK_STATE_MACHINE.md",
                      "section": "TaskContext",
                      "score": 0.5
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        coEvery {
            multiServerRepository.callTool(eq("answer_with_retrieval"), any())
        } returns StringResult(retrievalPayload)

        val result = orchestrator.detectAndExecuteTool(userInput)

        assertTrue(result is ToolExecutionResult.Success)
        val successResult = result as ToolExecutionResult.Success
        assertTrue(successResult.context.contains("DOCUMENT RETRIEVAL"))
        assertTrue(successResult.context.contains("retrievalApplied: true"))
        assertTrue(successResult.context.contains("TASK_STATE_MACHINE.md"))
        assertNotNull(successResult.retrievalSummary)
        assertEquals("local_docs", successResult.retrievalSummary?.source)
        assertEquals("TASK_STATE_MACHINE.md", successResult.retrievalSummary?.chunks?.firstOrNull()?.title)

        coVerify(exactly = 1) {
            multiServerRepository.callTool(
                eq("answer_with_retrieval"),
                match { params ->
                    params["query"] == userInput &&
                        params["source"] == com.example.aiadventchallenge.data.mcp.DocumentRetrievalConfig.defaultSource &&
                        params["strategy"] == com.example.aiadventchallenge.data.mcp.DocumentRetrievalConfig.DEFAULT_STRATEGY
                }
            )
        }
    }

    @Test
    fun `knowledge retrieval can be disabled for explicit rag mode`() = runTest {
        val userInput = "Объясни по документации проекта как устроен retrieval"

        val result = orchestrator.detectAndExecuteTool(
            userInput = userInput,
            allowKnowledgeRetrieval = false
        )

        assertEquals(ToolExecutionResult.NoToolFound, result)

        coVerify(exactly = 0) {
            multiServerRepository.callTool(eq("answer_with_retrieval"), any())
        }
    }
}
