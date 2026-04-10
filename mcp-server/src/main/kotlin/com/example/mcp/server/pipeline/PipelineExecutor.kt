package com.example.mcp.server.pipeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PipelineExecutor(
    private val enableLogging: Boolean = true
) {

    suspend fun execute(
        steps: List<PipelineStep<*, *>>,
        initialInput: Any,
        context: PipelineContext
    ): PipelineResult<*> {
        if (enableLogging) {
            println("🚀 Starting pipeline: ${context.pipelineName} (${context.pipelineId})")
        }

        var currentInput: Any = initialInput as Any
        var currentContext = context

        for (step in steps) {
            @Suppress("UNCHECKED_CAST")
            val typedStep = step as PipelineStep<Any, Any>

            if (enableLogging) {
                println("   ⏭️  Executing step: ${typedStep.stepName}")
                println("      Description: ${typedStep.description}")
            }

            val result = typedStep.execute(currentInput, currentContext)

            when (result) {
                is PipelineResult.Success -> {
                    currentInput = result.data
                    currentContext = currentContext.setData(step.stepName, result.data)

                    if (enableLogging) {
                        println("      ✅ Step completed successfully")
                    }
                }
                is PipelineResult.Failure -> {
                    if (enableLogging) {
                        println("      ❌ Step failed: ${result.errorMessage}")
                        println("   💔 Pipeline execution stopped at step: ${step.stepName}")
                    }
                    return result
                }
            }
        }

        if (enableLogging) {
            println("   ✅ Pipeline completed successfully")
        }

        return PipelineResult.Success(
            stepName = "pipeline_complete",
            data = currentInput
        )
    }

    suspend fun <T> executeStep(
        step: PipelineStep<T, *>,
        input: T,
        context: PipelineContext
    ): PipelineResult<*> {
        if (enableLogging) {
            println("🔧 Executing single step: ${step.stepName}")
        }

        return withContext(Dispatchers.IO) {
            step.execute(input, context)
        }
    }

    companion object {
        fun create(enableLogging: Boolean = true): PipelineExecutor {
            return PipelineExecutor(enableLogging)
        }
    }
}