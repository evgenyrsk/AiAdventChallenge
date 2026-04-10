package com.example.mcp.server.pipeline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface PipelineStep<INPUT, OUTPUT> {
    val stepName: String
    val description: String

    suspend fun execute(input: INPUT, context: PipelineContext): PipelineResult<OUTPUT>
}

abstract class AbstractPipelineStep<INPUT, OUTPUT>(
    override val stepName: String,
    override val description: String
) : PipelineStep<INPUT, OUTPUT> {

    protected abstract suspend fun doExecute(input: INPUT, context: PipelineContext): PipelineResult<OUTPUT>

    override suspend fun execute(input: INPUT, context: PipelineContext): PipelineResult<OUTPUT> {
        return withContext(Dispatchers.IO) {
            try {
                doExecute(input, context)
            } catch (e: Exception) {
                PipelineResult.Failure(
                    stepName = stepName,
                    errorMessage = e.message ?: "Unknown error"
                )
            }
        }
    }
}