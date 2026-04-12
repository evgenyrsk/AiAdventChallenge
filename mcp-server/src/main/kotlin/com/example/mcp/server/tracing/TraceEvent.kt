package com.example.mcp.server.tracing

import kotlinx.serialization.Serializable

@Serializable
data class TraceEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val flowId: String,
    val stepId: String? = null,
    val eventType: TraceEventType,
    val serverId: String,
    val toolName: String,
    val durationMs: Long = 0,
    val input: Map<String, String>? = null,
    val output: Map<String, String>? = null,
    val error: String? = null
)

@Serializable
enum class TraceEventType {
    FLOW_STARTED,
    FLOW_COMPLETED,
    FLOW_FAILED,
    STEP_STARTED,
    STEP_COMPLETED,
    STEP_FAILED,
    SERVER_CALLED,
    TOOL_EXECUTED,
    ROUTING_DECISION,
    VALIDATION_CHECK
}
