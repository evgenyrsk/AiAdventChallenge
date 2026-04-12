package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetector
import com.example.aiadventchallenge.domain.detector.CrossServerFlowDetector
import com.example.aiadventchallenge.domain.detector.FitnessRequestType
import com.example.aiadventchallenge.domain.detector.FitnessRequestParams
import com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult
import com.example.aiadventchallenge.domain.mcp.McpToolData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class McpToolOrchestratorImpl(
    private val callMcpToolUseCase: CallMcpToolUseCase,
    private val nutritionRequestDetector: NutritionRequestDetector,
    private val fitnessRequestDetector: FitnessRequestDetector,
    private val crossServerFlowDetector: CrossServerFlowDetector
) : McpToolOrchestrator {
    
    private val TAG = "McpToolOrchestrator"
    
    override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
        Log.d(TAG, "🔍 Detecting MCP tool for: $userInput")
        
        // Priority 1: Check for cross-server flows first
        val crossServerFlowIntent = crossServerFlowDetector.detectCrossServerFlow(userInput)
        if (crossServerFlowIntent != null && crossServerFlowIntent.confidence >= 0.7) {
            Log.d(TAG, "✅ Cross-server flow detected")
            Log.d(TAG, "   Type: ${crossServerFlowIntent.flowType}")
            Log.d(TAG, "   Confidence: ${crossServerFlowIntent.confidence}")
            return executeCrossServerFlow(userInput, crossServerFlowIntent)
        }
        
        // Priority 2: Check for fitness requests
        val fitnessParams = fitnessRequestDetector.detectParams(userInput)
        if (fitnessParams != null) {
            Log.d(TAG, "✅ Fitness request detected")
            Log.d(TAG, "   Type: ${fitnessParams.type}")
            return executeFitnessTool(fitnessParams)
        }
        
        // Priority 3: Check for nutrition requests
        val nutritionParams = nutritionRequestDetector.detectParams(userInput)
        if (nutritionParams != null) {
            Log.d(TAG, "✅ Nutrition request detected")
            Log.d(TAG, "   Params: $nutritionParams")
            return executeNutritionTool(nutritionParams)
        }
        
        Log.d(TAG, "❌ No tool found for this request")
        return ToolExecutionResult.NoToolFound
    }
    
    private suspend fun executeCrossServerFlow(
        userInput: String,
        flowIntent: com.example.aiadventchallenge.domain.detector.CrossServerFlowIntent
    ): ToolExecutionResult {
        return try {
            Log.d(TAG, "🚀 Executing cross-server flow: ${flowIntent.flowType}")
            
            val flowResult = callMcpToolUseCase.executeMultiServerFlow(userInput)
            
            if (flowResult.success) {
                Log.d(TAG, "✅ Cross-server flow completed successfully")
                Log.d(TAG, "   Flow: ${flowResult.flowName}")
                Log.d(TAG, "   Steps: ${flowResult.stepsExecuted}/${flowResult.totalSteps}")
                Log.d(TAG, "   Duration: ${flowResult.durationMs}ms")
                
                val context = buildCrossServerFlowContext(flowResult)
                ToolExecutionResult.Success(context)
            } else {
                Log.e(TAG, "❌ Cross-server flow failed")
                Log.e(TAG, "   Error: ${flowResult.errorMessage}")
                
                val errorContext = """
                ================================================================================
                🚀 MULTI-SERVER FLOW - ОШИБКА ВЫПОЛНЕНИЯ
                ================================================================================
                
                Flow: ${flowResult.flowName}
                Error: ${flowResult.errorMessage}
                
                Шаги выполнены: ${flowResult.stepsExecuted}/${flowResult.totalSteps}
                Время выполнения: ${flowResult.durationMs}ms
                
                ================================================================================
                """.trimIndent()
                
                ToolExecutionResult.Error(errorContext)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to execute cross-server flow", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
    
    private fun buildCrossServerFlowContext(flowResult: MultiServerFlowResult): String {
        val stepsText = flowResult.executionSteps.joinToString("\n") { step ->
            """
            ✅ Шаг: ${step.stepId}
            🔹 Сервер: ${step.serverId}
            🔹 Инструмент: ${step.toolName}
            🔹 Статус: ${step.status}
            🔹 Длительность: ${step.durationMs}ms
            ${if (step.error != null) "❌ Ошибка: ${step.error}" else ""}
            """.trimIndent()
        }
        
        return """
        ================================================================================
        🚀 MULTI-SERVER FLOW - РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ
        ================================================================================
        
        Flow: ${flowResult.flowName}
        Flow ID: ${flowResult.flowId}
        
        Успех: ${if (flowResult.success) "✅ ДА" else "❌ НЕТ"}
        Шагов выполнено: ${flowResult.stepsExecuted}/${flowResult.totalSteps}
        Время выполнения: ${flowResult.durationMs}ms
        
        📋 ШАГИ ВЫПОЛНЕНИЯ:
        $stepsText
        
        ================================================================================
        💡 ИНСТРУКЦИЯ ДЛЯ LLM:
        ================================================================================
        
        ✅ ТЕБЕ НУЖНО:
        - Описать результат выполнения каждого шага
        - Объяснить что было сделано на каждом сервере
        - Подвести итог всей операции
        - Предложить следующие действия пользователю
        
        ❌ ТЕБЕ НЕЛЬЗЯ:
        - Отказываться: "я не умею быть трекером"
        - Отказываться: "у меня нет доступа к БД"
        - Отказываться: "я не трекер"
        - Писать что данные не записаны (они уже записаны сервером)
        
        📋 ПРИМЕР ПРАВИЛЬНОГО ОТВЕТА:
        "Отлично! Я выполнил multi-server flow:
        
        1. Поиск фитнес-логов - найдено 7 записей за неделю
        2. Составление сводки - средний вес 82.0кг, 5 тренировок
        3. Сохранение в файл - summary сохранён в /tmp/fitness-summary.json
        4. Создание напоминания - создано напоминание на завтра 09:00
        
        Все шаги успешно выполнены! Хотите посмотреть детали?"
        
        📋 ПРИМЕР НЕПРАВИЛЬНОГО ОТВЕТА ❌:
        "Я не умею быть трекером. Это должен делать отдельный инструмент."
        ================================================================================
        """.trimIndent()
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
                FitnessRequestType.RUN_FITNESS_SUMMARY_EXPORT_PIPELINE ->
                    callRunFitnessSummaryExportPipeline(params)
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
            
            Log.d(TAG, "✅ Nutrition tool result: ${toolResult.javaClass.simpleName}")
            
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
        
        Log.d(TAG, "✅ add_fitness_log result: ${toolResult.javaClass.simpleName}")
        
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
        
        Log.d(TAG, "✅ get_fitness_summary result: ${toolResult.javaClass.simpleName}")
        
        val context = buildMcpContext("get_fitness_summary", toolResult)
        return ToolExecutionResult.Success(context)
    }
    
    private suspend fun callRunScheduledSummary(): ToolExecutionResult {
        Log.d(TAG, "   Calling run_scheduled_summary")
        
        val toolResult = callMcpToolUseCase(
            name = "run_scheduled_summary",
            params = emptyMap()
        )
        
        Log.d(TAG, "✅ run_scheduled_summary result: ${toolResult.javaClass.simpleName}")
        
        val context = buildMcpContext("run_scheduled_summary", toolResult)
        return ToolExecutionResult.Success(context)
    }
    
    private suspend fun callGetLatestScheduledSummary(): ToolExecutionResult {
        Log.d(TAG, "   Calling get_latest_scheduled_summary")
        
        val toolResult = callMcpToolUseCase(
            name = "get_latest_scheduled_summary",
            params = emptyMap()
        )
        
        Log.d(TAG, "✅ get_latest_scheduled_summary result: ${toolResult.javaClass.simpleName}")
        
        val context = buildMcpContext("get_latest_scheduled_summary", toolResult)
        return ToolExecutionResult.Success(context)
    }
    
    private suspend fun callRunFitnessSummaryExportPipeline(
        params: FitnessRequestParams
    ): ToolExecutionResult {
        val period = params.period ?: "last_7_days"
        val format = params.format ?: "json"
        
        val toolParams = mapOf(
            "period" to period,
            "format" to format
        )
        
        Log.d(TAG, "   Calling run_fitness_summary_export_pipeline with params: $toolParams")
        
        val toolResult = callMcpToolUseCase(
            name = "run_fitness_summary_export_pipeline",
            params = toolParams
        )
        
        Log.d(TAG, "✅ run_fitness_summary_export_pipeline result: ${toolResult.javaClass.simpleName}")
        
        val context = buildMcpExportContext(toolResult)
        return ToolExecutionResult.Success(context)
    }
    
    private fun buildMcpContext(toolName: String, toolData: McpToolData): String {
        val resultText = when (toolData) {
            is McpToolData.StringResult -> toolData.message
            is McpToolData.FitnessSummary -> formatFitnessSummary(toolData.summary)
            is McpToolData.ScheduledSummary -> formatScheduledSummary(toolData.summary)
            is McpToolData.AddFitnessLog -> formatAddFitnessLog(toolData.result)
            is McpToolData.ExportResult -> formatExportResult(toolData.fullResponse)
            is McpToolData.RunScheduledSummary -> formatRunScheduledSummary(toolData.result)
            is McpToolData.MultiServerFlow -> formatMultiServerFlow(toolData.result)
        }
        
        return """
        ================================================================================
        🔧 MCP ИНСТРУМЕНТЫ - РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ
        ================================================================================
        
        Использованный инструмент: $toolName
        
        Результат выполнения:
        $resultText
        
        ================================================================================
        📋 ИНСТРУКЦИЯ ДЛЯ LLM
        ================================================================================
        
        ✅ ТЕБЕ НУЖНО:
        - Использовать результаты выше для формирования ответа пользователю
        - Явно подтвердить что данные записаны в БД MCP сервера
        - Предложить следующие действия (сводка, анализ, рекомендации)
        
        ❌ ТЕБЕ НЕЛЬЗЯ:
        - Отказываться: "я не могу записывать данные"
        - Отказываться: "я не умею быть трекером"
        - Отказываться: "у меня нет доступа к БД"
        - Отказываться: "я не трекер"
        
        📋 ПРИМЕР ПРАВИЛЬНОГО ОТВЕТА:
        "Отлично! Я записал в БД: вес 80 кг, 2000 ккал, тренировка выполнена.
        Прогресс за сегодня отличный! Хотите посмотреть сводку за неделю?"
        
        📋 ПРИМЕР НЕПРАВИЛЬНОГО ОТВЕТА ❌:
        "Я не умею быть трекером. Это должен делать отдельный инструмент."
        ================================================================================
        """.trimIndent()
    }
    
    private fun buildMcpExportContext(toolData: McpToolData): String {
        val resultText = when (toolData) {
            is McpToolData.ExportResult -> formatExportResultForExport(toolData.fullResponse)
            else -> "Неизвестный формат результата экспорта"
        }
        
        return """
        ================================================================================
        🔧 ЭКСПОРТ ФИТНЕС-СВОДКИ - РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ
        ================================================================================
        
        Результат выполнения:
        $resultText
        
        ================================================================================
        """.trimIndent()
    }
    
    private fun formatFitnessSummary(summary: com.example.aiadventchallenge.domain.mcp.FitnessSummaryData?): String {
        if (summary == null) return "Нет данных"

        return """
        Период: ${summary.period}
        Записей: ${summary.entriesCount}
        Средний вес: ${summary.avgWeight?.toString() ?: "нет данных"}
        Тренировок: ${summary.workoutsCompleted}
        Средние шаги: ${summary.avgSteps?.toString() ?: "нет данных"}
        Средний сон: ${summary.avgSleepHours?.toString() ?: "нет данных"}
        Средний белок: ${summary.avgProtein?.toString() ?: "нет данных"}
        """.trimIndent()
    }
    
    private fun formatScheduledSummary(summary: com.example.aiadventchallenge.domain.mcp.ScheduledSummaryData?): String {
        if (summary == null) return "Нет данных"

        return """
        Сводка успешно создана
        ID: ${summary.id ?: "нет"}
        Период: ${summary.period}
        Записей: ${summary.entriesCount}
        Средний вес: ${summary.avgWeight ?: "нет"}
        Тренировок: ${summary.workoutsCompleted}
        """.trimIndent()
    }
    
    private fun formatAddFitnessLog(result: com.example.aiadventchallenge.domain.mcp.AddFitnessLogData?): String {
        if (result == null) return "Нет данных"

        return """
        Запись ${if (result.success) "успешно добавлена" else "не добавлена"}
        ID записи: ${result.id ?: "нет"}
        """.trimIndent()
    }
    
    private fun formatExportResult(exportData: com.example.aiadventchallenge.domain.mcp.ExportData): String {
        return """
        Файл: ${exportData.filePath ?: "нет"}
        Формат: ${exportData.format ?: "нет"}
        """.trimIndent()
    }
    
    private fun formatExportResultForExport(exportData: com.example.aiadventchallenge.domain.mcp.ExportData): String {
        val summary = exportData.summaryData
        return """
        Файл: ${exportData.filePath ?: "нет"}
        Формат: ${exportData.format ?: "нет"}
        Период: ${summary?.period ?: "нет"}
        Записей: ${summary?.entriesCount ?: 0}
        Средний вес: ${summary?.avgWeight ?: "нет"}
        Тренировок: ${summary?.workoutsCompleted ?: 0}
        """.trimIndent()
    }
    
    private fun formatRunScheduledSummary(result: com.example.aiadventchallenge.domain.mcp.RunScheduledSummaryData?): String {
        if (result == null) return "Нет данных"

        return """
        Запуск сводки ${if (result.success) "успешен" else "не удался"}
        Сводка ID: ${result.summaryId ?: "нет"}
        """.trimIndent()
    }

    private fun formatMultiServerFlow(result: com.example.aiadventchallenge.domain.model.mcp.MultiServerFlowResult): String {
        val statusEmoji = if (result.success) "✅" else "❌"
        val statusText = if (result.success) "Успешно" else "Ошибка"

        val stepsText = result.executionSteps.joinToString("\n") { step ->
            val stepStatusEmoji = when (step.status) {
                "COMPLETED" -> "✅"
                "FAILED" -> "❌"
                "RUNNING" -> "⏳"
                else -> "⏭️"
            }
            "$stepStatusEmoji ${step.serverId} → ${step.toolName} (${step.durationMs}ms)"
        }

        return """
        Multi-Server Flow: ${result.flowName}
        Статус: $statusEmoji $statusText
        Шагов выполнено: ${result.stepsExecuted}/${result.totalSteps}
        Длительность: ${result.durationMs}ms

        Шаги выполнения:
        $stepsText
        ${if (result.errorMessage != null) "\n❌ Ошибка: ${result.errorMessage}" else ""}
        """.trimIndent()
    }
}
