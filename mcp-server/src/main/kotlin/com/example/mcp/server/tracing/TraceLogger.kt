package com.example.mcp.server.tracing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class TraceLogger(
    private val storage: TraceStorage = TraceStorage(),
    private val json: Json = Json { prettyPrint = true }
) {
    private val currentTraces = ConcurrentHashMap<String, ExecutionTrace>()
    
    fun startTrace(
        flowId: String,
        flowName: String,
        metadata: Map<String, String> = emptyMap()
    ): ExecutionTrace {
        val trace = ExecutionTrace(
            flowId = flowId,
            flowName = flowName,
            metadata = metadata.toMutableMap()
        )
        
        trace.addEvent(
            TraceEvent(
                flowId = flowId,
                eventType = TraceEventType.FLOW_STARTED,
                serverId = "orchestrator",
                toolName = "flow_start"
            )
        )
        
        currentTraces[flowId] = trace
        storage.save(trace)
        
        println("🔵 [TRACE] Flow started: $flowName ($flowId)")
        
        return trace
    }
    
    fun logStepStarted(
        flowId: String,
        stepId: String,
        serverId: String,
        toolName: String,
        input: Map<String, String>? = null
    ) {
        val trace = currentTraces[flowId] ?: return
        
        val event = TraceEvent(
            flowId = flowId,
            stepId = stepId,
            eventType = TraceEventType.STEP_STARTED,
            serverId = serverId,
            toolName = toolName,
            input = input
        )
        
        trace.addEvent(event)
        storage.save(trace)
        
        println("🟢 [TRACE] Step started: $stepId -> $toolName@$serverId")
    }
    
    fun logStepCompleted(
        flowId: String,
        stepId: String,
        serverId: String,
        toolName: String,
        durationMs: Long,
        output: Map<String, String>? = null
    ) {
        val trace = currentTraces[flowId] ?: return
        
        val event = TraceEvent(
            flowId = flowId,
            stepId = stepId,
            eventType = TraceEventType.STEP_COMPLETED,
            serverId = serverId,
            toolName = toolName,
            durationMs = durationMs,
            output = output
        )
        
        trace.addEvent(event)
        storage.save(trace)
        
        println("✅ [TRACE] Step completed: $stepId -> $toolName@$serverId (${durationMs}ms)")
    }
    
    fun logStepFailed(
        flowId: String,
        stepId: String,
        serverId: String,
        toolName: String,
        durationMs: Long,
        error: String
    ) {
        val trace = currentTraces[flowId] ?: return
        
        val event = TraceEvent(
            flowId = flowId,
            stepId = stepId,
            eventType = TraceEventType.STEP_FAILED,
            serverId = serverId,
            toolName = toolName,
            durationMs = durationMs,
            error = error
        )
        
        trace.addEvent(event)
        storage.save(trace)
        
        println("❌ [TRACE] Step failed: $stepId -> $toolName@$serverId ($error)")
    }
    
    fun logToolExecuted(
        flowId: String,
        serverId: String,
        toolName: String,
        durationMs: Long,
        output: Map<String, String>? = null
    ) {
        val trace = currentTraces[flowId] ?: return
        
        val event = TraceEvent(
            flowId = flowId,
            eventType = TraceEventType.TOOL_EXECUTED,
            serverId = serverId,
            toolName = toolName,
            durationMs = durationMs,
            output = output
        )
        
        trace.addEvent(event)
        storage.save(trace)
    }
    
    fun logRoutingDecision(
        flowId: String,
        serverId: String,
        toolName: String,
        reason: String
    ) {
        val trace = currentTraces[flowId] ?: return
        
        val event = TraceEvent(
            flowId = flowId,
            eventType = TraceEventType.ROUTING_DECISION,
            serverId = serverId,
            toolName = toolName,
            input = mapOf("reason" to reason)
        )
        
        trace.addEvent(event)
        storage.save(trace)
        
        println("🔀 [TRACE] Routing: $toolName -> $serverId ($reason)")
    }
    
    fun logValidationCheck(
        flowId: String,
        toolName: String,
        passed: Boolean,
        message: String? = null
    ) {
        val trace = currentTraces[flowId] ?: return
        
        val event = TraceEvent(
            flowId = flowId,
            eventType = TraceEventType.VALIDATION_CHECK,
            serverId = "orchestrator",
            toolName = toolName,
            input = mapOf(
                "passed" to passed.toString(),
                "message" to (message ?: "")
            )
        )
        
        trace.addEvent(event)
        storage.save(trace)
    }
    
    fun completeTrace(
        flowId: String,
        success: Boolean = true,
        metadata: Map<String, String> = emptyMap()
    ) {
        val trace = currentTraces[flowId] ?: return
        
        if (success) {
            trace.markAsCompleted()
            
            val event = TraceEvent(
                flowId = flowId,
                eventType = TraceEventType.FLOW_COMPLETED,
                serverId = "orchestrator",
                toolName = "flow_complete"
            )
            
            trace.addEvent(event)
            
            println("✅ [TRACE] Flow completed: ${trace.flowName} ($flowId) in ${trace.getDuration()}ms")
        } else {
            trace.markAsCompleted()
            trace.status = TraceStatus.FAILED
            
            val event = TraceEvent(
                flowId = flowId,
                eventType = TraceEventType.FLOW_FAILED,
                serverId = "orchestrator",
                toolName = "flow_failed",
                input = metadata
            )
            
            trace.addEvent(event)
            
            println("❌ [TRACE] Flow failed: ${trace.flowName} ($flowId) in ${trace.getDuration()}ms")
        }
        
        trace.metadata.putAll(metadata)
        storage.save(trace)
        
        currentTraces.remove(flowId)
    }
    
    fun getTrace(flowId: String): ExecutionTrace? {
        return storage.getByFlowId(flowId)
    }
    
    fun getAllTraces(): List<ExecutionTrace> {
        return storage.getAll()
    }
    
    fun getRecentTraces(limit: Int = 10): List<ExecutionTrace> {
        return storage.getRecent(limit)
    }
    
    fun getTraceJson(flowId: String): String? {
        return getTrace(flowId)?.let { json.encodeToString(it) }
    }
    
    fun clear() {
        currentTraces.clear()
        storage.clear()
    }
    
    fun getStats(): TraceStorageStats {
        return storage.getStats()
    }
}
