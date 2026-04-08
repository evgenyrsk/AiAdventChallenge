package com.example.aiadventchallenge.domain.mcp

import android.util.Log
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import com.example.aiadventchallenge.domain.model.mcp.CalculateNutritionParams
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector

class McpToolOrchestratorImpl(
    private val callMcpToolUseCase: CallMcpToolUseCase,
    private val nutritionRequestDetector: NutritionRequestDetector
) : McpToolOrchestrator {
    
    private val TAG = "McpToolOrchestrator"
    
    override suspend fun detectAndExecuteTool(userInput: String): ToolExecutionResult {
        val nutritionParams = nutritionRequestDetector.detectParams(userInput)
        
        if (nutritionParams == null) {
            return ToolExecutionResult.NoToolFound
        }
        
        Log.d(TAG, "🔍 Nutrition request detected")
        Log.d(TAG, "   Params: $nutritionParams")
        
        return try {
            val toolResult = callMcpToolUseCase(
                name = "calculate_nutrition_plan",
                params = mapOf(
                    "sex" to nutritionParams.sex,
                    "age" to nutritionParams.age,
                    "heightCm" to nutritionParams.heightCm,
                    "weightKg" to nutritionParams.weightKg,
                    "activityLevel" to nutritionParams.activityLevel,
                    "goal" to nutritionParams.goal
                )
            )
            
            Log.d(TAG, "✅ MCP tool result: $toolResult")
            
            val context = """
                
================================================================================
📊 РАСЧЕТ ПИТАНИЯ (из MCP инструмента)
================================================================================
$toolResult
================================================================================

Используй эти данные для формирования ответа пользователю.
================================================================================
            """.trimIndent()
            
            ToolExecutionResult.Success(context)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to call MCP nutrition tool", e)
            ToolExecutionResult.Error(e.message ?: "Неизвестная ошибка")
        }
    }
}
