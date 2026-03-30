package com.example.aiadventchallenge.domain.usecase

import android.util.Log
import com.example.aiadventchallenge.data.config.ModelVersionsConfig
import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.ModelComparisonBatch
import com.example.aiadventchallenge.domain.model.ModelComparisonResult
import com.example.aiadventchallenge.domain.model.ModelStrength
import com.example.aiadventchallenge.domain.model.ModelVersion
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.RequestType
import com.example.aiadventchallenge.domain.repository.AiRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AskModelUseCase(
    private val repository: AiRepository
) {

    suspend operator fun invoke(prompt: String): ModelComparisonBatch {
        Log.d(TAG, "========================================")
        Log.d(TAG, "Starting comparison of ${ModelVersionsConfig.getAllModels().size} models")
        Log.d(TAG, "========================================")

        return coroutineScope {
            val models = ModelVersionsConfig.getAllModels()
            
            val results = models.map { modelVersion ->
                async {
                    executeModelRequest(modelVersion, prompt)
                }
            }.awaitAll()

            Log.d(TAG, "========================================")
            Log.d(TAG, "All requests completed")
            Log.d(TAG, "Total models: ${results.size}")
            Log.d(TAG, "Successful: ${results.count { it.error == null }}")
            Log.d(TAG, "Failed: ${results.count { it.error != null }}")
            Log.d(TAG, "========================================")

            ModelComparisonBatch(
                prompt = prompt,
                results = results.associateBy { it.modelVersion.strength }
            )
        }
    }

    private suspend fun executeModelRequest(
        modelVersion: ModelVersion,
        prompt: String
    ): ModelComparisonResult {
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "=== Starting request to ${modelVersion.modelName} (${modelVersion.modelId}) ===")
        Log.d(TAG, "Prompt: $prompt")

        return try {
            val config = RequestConfig(
                systemPrompt = Prompts.UNLIMITED_SYSTEM_PROMPT,
                modelId = modelVersion.modelId,
                maxTokens = 3000,
            )

            val result = repository.askWithUsage(prompt, null, config, RequestType.MODEL_TEST)
            
            val latencyMs = System.currentTimeMillis() - startTime

            when (result) {
                is ChatResult.Success -> {
                    val answerWithUsage = result.data
                    val promptTokens = answerWithUsage.promptTokens ?: 0
                    val completionTokens = answerWithUsage.completionTokens ?: 0
                    val cost = calculateCost(
                        promptTokens,
                        completionTokens,
                        modelVersion.inputPricePer1M,
                        modelVersion.outputPricePer1M
                    )

                    Log.d(TAG, "=== Success: ${modelVersion.modelName} ===")
                    Log.d(TAG, "Latency: ${latencyMs}ms")
                    Log.d(TAG, "Tokens: prompt=$promptTokens, completion=$completionTokens, total=${answerWithUsage.totalTokens}")
                    Log.d(TAG, "Cost: \$${String.format("%.6f", cost)}")
                    Log.d(TAG, "Response: ${answerWithUsage.content.take(500)}${if (answerWithUsage.content.length > 500) "..." else ""}")

                    ModelComparisonResult(
                        modelVersion = modelVersion,
                        prompt = prompt,
                        response = answerWithUsage.content,
                        latencyMs = latencyMs,
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens,
                        cost = cost,
                        error = null
                    )
                }
                is ChatResult.Error -> {
                    Log.e(TAG, "=== Error: ${modelVersion.modelName} ===")
                    Log.e(TAG, "Error message: ${result.message}")
                    Log.e(TAG, "Latency: ${latencyMs}ms")

                    ModelComparisonResult(
                        modelVersion = modelVersion,
                        prompt = prompt,
                        response = "",
                        latencyMs = latencyMs,
                        promptTokens = null,
                        completionTokens = null,
                        totalTokens = null,
                        cost = 0.0,
                        error = result.message
                    )
                }
            }
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startTime
            Log.e(TAG, "=== Exception: ${modelVersion.modelName} ===")
            Log.e(TAG, "Exception: ${e.message}", e)
            Log.e(TAG, "Latency: ${latencyMs}ms")

            ModelComparisonResult(
                modelVersion = modelVersion,
                prompt = prompt,
                response = "",
                latencyMs = latencyMs,
                promptTokens = null,
                completionTokens = null,
                totalTokens = null,
                cost = 0.0,
                error = e.message ?: "Unknown error"
            )
        }
    }

    companion object {
        private const val TAG = "AskModelUseCase"
    }

    private fun calculateCost(
        promptTokens: Int,
        completionTokens: Int,
        inputPricePer1M: Double,
        outputPricePer1M: Double
    ): Double {
        val inputCost = (promptTokens.toDouble() / 1_000_000) * inputPricePer1M
        val outputCost = (completionTokens.toDouble() / 1_000_000) * outputPricePer1M
        return inputCost + outputCost
    }
}
