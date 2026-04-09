package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetector
import com.example.aiadventchallenge.domain.detector.FitnessRequestType
import com.example.aiadventchallenge.domain.detector.FitnessRequestParams
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class McpToolOrchestratorImpl(
    private val callMcpToolUseCase: CallMcpToolUseCase,
    private val nutritionRequestDetector: NutritionRequestDetector,
    private val fitnessRequestDetector: FitnessRequestDetector
) : McpToolOrchestrator {
    
    private val TAG = "McpToolOrchestrator"
    
    override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
        Log.d(TAG, "🔍 Detecting MCP tool for: $userInput")

        val fitnessParams = fitnessRequestDetector.detectParams(userInput)
        if (fitnessParams != null) {
            Log.d(TAG, "✅ Fitness request detected")
            Log.d(TAG, "   Type: ${fitnessParams.type}")
            return executeFitnessTool(fitnessParams)
        }

        val nutritionParams = nutritionRequestDetector.detectParams(userInput)
        if (nutritionParams != null) {
            Log.d(TAG, "✅ Nutrition request detected")
            Log.d(TAG, "   Params: $nutritionParams")
            return executeNutritionTool(nutritionParams)
        }

        Log.d(TAG, "❌ No tool found for this request")
        return ToolExecutionResult.NoToolFound
    }

    private suspend fun executeFitnessTool(
        params: FitnessRequestParams
    ): ToolExecutionResult {
        return try {
            when (params.type) {
                FitnessRequestType.ADD_FITNESS_LOG ->
                    callAddFitnessLog(params)
                FitnessRequestType.GET_FITNESS_SUMMARY ->
                    callGetFitnessSummary(params)
                FitnessRequestType.RUN_SCHEDULED_SUMMARY ->
                    callRunScheduledSummary()
                FitnessRequestType.GET_LATEST_SUMMARY ->
                    callGetLatestScheduledSummary()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to call fitness tool", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    private suspend fun executeNutritionTool(
        params: CalculateNutritionParams
    ): ToolExecutionResult {
        return try {
            val toolResult = callMcpToolUseCase(
                name = "calculate_nutrition_plan",
                params = mapOf(
                    "sex" to params.sex,
                    "age" to params.age,
                    "heightCm" to params.heightCm,
                    "weightKg" to params.weightKg,
                    "activityLevel" to params.activityLevel,
                    "goal" to params.goal
                )
            )

            Log.d(TAG, "✅ Nutrition tool result: $toolResult")

            val context = buildMcpContext("calculate_nutrition_plan", toolResult)
            ToolExecutionResult.Success(context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to call MCP nutrition tool", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }

    private suspend fun callAddFitnessLog(
        params: FitnessRequestParams
    ): ToolExecutionResult {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        val toolParams = mutableMapOf<String, Any?>(
            "date" to (params.date ?: today)
        )
        params.weight?.let { toolParams["weight"] = it }
        params.calories?.let { toolParams["calories"] = it }
        params.protein?.let { toolParams["protein"] = it }
        params.workoutCompleted?.let { toolParams["workoutCompleted"] = it }
        params.steps?.let { toolParams["steps"] = it }
        params.sleepHours?.let { toolParams["sleepHours"] = it }
        params.notes?.let { toolParams["notes"] = it }

        Log.d(TAG, "   Calling add_fitness_log with params: $toolParams")

        val toolResult = callMcpToolUseCase(
            name = "add_fitness_log",
            params = toolParams
        )

        Log.d(TAG, "✅ add_fitness_log result: $toolResult")

        val context = buildMcpContext("add_fitness_log", toolResult)
        return ToolExecutionResult.Success(context)
    }

    private suspend fun callGetFitnessSummary(
        params: FitnessRequestParams
    ): ToolExecutionResult {
        val period = params.period ?: "last_7_days"

        val toolParams = mapOf("period" to period)

        Log.d(TAG, "   Calling get_fitness_summary with params: $toolParams")

        val toolResult = callMcpToolUseCase(
            name = "get_fitness_summary",
            params = toolParams
        )

        Log.d(TAG, "✅ get_fitness_summary result: $toolResult")

        val context = buildMcpContext("get_fitness_summary", toolResult)
        return ToolExecutionResult.Success(context)
    }

    private suspend fun callRunScheduledSummary(): ToolExecutionResult {
        Log.d(TAG, "   Calling run_scheduled_summary")

        val toolResult = callMcpToolUseCase(
            name = "run_scheduled_summary",
            params = emptyMap()
        )

        Log.d(TAG, "✅ run_scheduled_summary result: $toolResult")

        val context = buildMcpContext("run_scheduled_summary", toolResult)
        return ToolExecutionResult.Success(context)
    }

    private suspend fun callGetLatestScheduledSummary(): ToolExecutionResult {
        Log.d(TAG, "   Calling get_latest_scheduled_summary")

        val toolResult = callMcpToolUseCase(
            name = "get_latest_scheduled_summary",
            params = emptyMap()
        )

        Log.d(TAG, "✅ get_latest_scheduled_summary result: $toolResult")

        val context = buildMcpContext("get_latest_scheduled_summary", toolResult)
        return ToolExecutionResult.Success(context)
    }

    private fun buildMcpContext(toolName: String, toolResult: String): String {
        return """

================================================================================
🔧 MCP ИНСТРУМЕНТЫ - РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ
================================================================================

Использованный инструмент: $toolName

Результат выполнения:
$toolResult

================================================================================
📋 ИНСТРУКЦИЯ ДЛЯ LLM
================================================================================

✅ ДАННЫЕ УЖЕ ЗАПИСАНЫ в БД MCP сервера.

❌ Тебе НЕЛЬЗЯ:
- Отказываться: "я не могу записывать данные"
- Отказываться: "я не умею быть трекером"
- Отказываться: "у меня нет доступа к БД"
- Отказываться: "я не трекер"

✅ ТЕБЕ НУЖНО:
- Использовать результаты выше для формирования ответа пользователю
- Явно подтвердить что данные записаны
- Предложить следующие действия (сводка, анализ, рекомендации)

📋 ПРИМЕР ПРАВИЛЬНОГО ОТВЕТА:
"Отлично! Я записал в БД: вес 80 кг, 2000 ккал, тренировка выполнена.
Прогресс за сегодня отличный! Хочешь посмотреть сводку за неделю?"

📋 ПРИМЕР НЕПРАВИЛЬНОГО ОТВЕТА ❌:
"Я не умею быть трекером. Это должен делать отдельный инструмент."
================================================================================
        """.trimIndent()
    }
}
