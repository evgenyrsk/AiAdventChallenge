package com.example.mcp.server.pipeline

sealed class PipelineResult<out T> {
    abstract val success: Boolean
    abstract val stepName: String
    abstract val errorMessage: String?

    data class Success<T>(
        override val stepName: String,
        val data: T,
        override val success: Boolean = true,
        override val errorMessage: String? = null
    ) : PipelineResult<T>()

    data class Failure(
        override val stepName: String,
        override val errorMessage: String,
        override val success: Boolean = false
    ) : PipelineResult<Nothing>()

    fun <R> map(transform: (T) -> R): PipelineResult<R> = when (this) {
        is Success -> Success(stepName, transform(data), success, errorMessage)
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> PipelineResult<R>): PipelineResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
    }
}