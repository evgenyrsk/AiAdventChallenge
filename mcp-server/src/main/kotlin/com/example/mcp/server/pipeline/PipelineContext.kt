package com.example.mcp.server.pipeline

data class PipelineContext(
    val pipelineId: String,
    val pipelineName: String,
    val startedAt: Long,
    private val data: MutableMap<String, Any?> = mutableMapOf()
) {
    fun <T> getData(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? T
    }

    fun <T> setData(key: String, value: T): PipelineContext {
        data[key] = value
        return this
    }

    companion object {
        fun create(pipelineId: String, pipelineName: String): PipelineContext {
            return PipelineContext(
                pipelineId = pipelineId,
                pipelineName = pipelineName,
                startedAt = System.currentTimeMillis()
            )
        }
    }
}