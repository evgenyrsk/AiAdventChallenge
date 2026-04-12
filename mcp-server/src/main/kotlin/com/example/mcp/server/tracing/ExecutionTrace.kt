package com.example.mcp.server.tracing

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ExecutionTrace(
    val traceId: String = UUID.randomUUID().toString(),
    val flowId: String,
    val flowName: String,
    val events: MutableList<TraceEvent> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    val metadata: MutableMap<String, String> = mutableMapOf(),
    var status: TraceStatus = TraceStatus.IN_PROGRESS
) {
    fun addEvent(event: TraceEvent) {
        events.add(event)
    }
    
    fun getDuration(): Long = (endTime ?: System.currentTimeMillis()) - startTime
    
    fun getStepTrace(stepId: String): List<TraceEvent> {
        return events.filter { it.stepId == stepId }
    }
    
    fun getEventsByType(eventType: TraceEventType): List<TraceEvent> {
        return events.filter { it.eventType == eventType }
    }
    
    fun getEventsByServer(serverId: String): List<TraceEvent> {
        return events.filter { it.serverId == serverId }
    }
    
    fun getEventsByTool(toolName: String): List<TraceEvent> {
        return events.filter { it.toolName == toolName }
    }
    
    fun markAsCompleted() {
        endTime = System.currentTimeMillis()
        status = TraceStatus.COMPLETED
    }
    
    fun markAsFailed(error: String) {
        endTime = System.currentTimeMillis()
        status = TraceStatus.FAILED
        metadata["error"] = error
    }
    
    fun getTotalStepCount(): Int {
        return events.count { it.eventType == TraceEventType.STEP_STARTED }
    }
    
    fun getCompletedStepCount(): Int {
        return events.count { it.eventType == TraceEventType.STEP_COMPLETED }
    }
    
    fun getFailedStepCount(): Int {
        return events.count { it.eventType == TraceEventType.STEP_FAILED }
    }
    
    fun getServerExecutionTime(serverId: String): Long {
        val serverEvents = getEventsByServer(serverId)
            .filter { it.eventType == TraceEventType.TOOL_EXECUTED }
        return serverEvents.sumOf { it.durationMs }
    }
}

@Serializable
enum class TraceStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
