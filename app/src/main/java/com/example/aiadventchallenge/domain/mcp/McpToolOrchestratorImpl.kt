package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase

class McpToolOrchestratorImpl(
    private val callMcpToolUseCase: CallMcpToolUseCase
) : McpToolOrchestrator {
    
    private val TAG = "McpToolOrchestrator"
    
    override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
        Log.d(TAG, "🔍 Checking for MCP tool in LLM response...")
        
        return ToolExecutionResult.NoToolFound
    }
    
    suspend fun executeTool(
        toolName: String,
        params: Map<String, Any?>
    ): ToolExecutionResult {
        return try {
            Log.d(TAG, "🔧 Calling MCP tool: $toolName")
            Log.d(TAG, "   Params: $params")
            
            val result = callMcpToolUseCase(toolName, params)
            
            Log.d(TAG, "✅ Tool result: ${result.javaClass.simpleName}")
            
            ToolExecutionResult.Success(formatResult(toolName, result))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to call MCP tool", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
    
    private fun formatResult(toolName: String, toolData: McpToolData): String {
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
        🔧 MCP ИНСТРУМЕНТ - РЕЗУЛЬТАТ ВЫПОЛНЕНИЯ
        ================================================================================
        
        Инструмент: $toolName
        
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
