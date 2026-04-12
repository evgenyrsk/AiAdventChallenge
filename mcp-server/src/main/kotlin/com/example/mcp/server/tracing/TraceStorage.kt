package com.example.mcp.server.tracing

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class TraceStorage(
    private val maxTraces: Int = 1000,
    private val maxTraceAgeMs: Long = 24 * 60 * 60 * 1000 // 24 hours
) {
    private val traces = ConcurrentHashMap<String, ExecutionTrace>()
    private val traceOrder = ConcurrentLinkedQueue<String>()
    
    fun save(trace: ExecutionTrace): Boolean {
        traceOrder.offer(trace.traceId)
        traces[trace.traceId] = trace
        
        cleanUpOldTraces()
        
        return true
    }
    
    fun get(traceId: String): ExecutionTrace? {
        return traces[traceId]
    }
    
    fun getByFlowId(flowId: String): ExecutionTrace? {
        return traces.values.find { it.flowId == flowId }
    }
    
    fun getAll(): List<ExecutionTrace> {
        return traces.values.toList()
    }
    
    fun getRecent(limit: Int = 10): List<ExecutionTrace> {
        return traceOrder.take(limit).mapNotNull { traces[it] }
    }
    
    fun delete(traceId: String): Boolean {
        traceOrder.remove(traceId)
        return traces.remove(traceId) != null
    }
    
    fun clear() {
        traces.clear()
        traceOrder.clear()
    }
    
    private fun cleanUpOldTraces() {
        val now = System.currentTimeMillis()
        
        // Remove traces older than maxTraceAgeMs
        val expiredTraces = traces.filter { (_, trace) ->
            (trace.endTime ?: now) - trace.startTime > maxTraceAgeMs
        }
        
        expiredTraces.forEach { (traceId, _) ->
            delete(traceId)
        }
        
        // If we still have too many traces, remove the oldest
        while (traces.size > maxTraces && traceOrder.isNotEmpty()) {
            val oldest = traceOrder.poll()
            if (oldest != null) {
                traces.remove(oldest)
            }
        }
    }
    
    fun getStats(): TraceStorageStats {
        val now = System.currentTimeMillis()
        val activeTraces = traces.values.count { it.status == TraceStatus.IN_PROGRESS }
        val completedTraces = traces.values.count { it.status == TraceStatus.COMPLETED }
        val failedTraces = traces.values.count { it.status == TraceStatus.FAILED }
        
        val avgDuration = traces.values
            .filter { it.endTime != null }
            .map { it.getDuration() }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0
        
        return TraceStorageStats(
            totalTraces = traces.size,
            activeTraces = activeTraces,
            completedTraces = completedTraces,
            failedTraces = failedTraces,
            avgDurationMs = avgDuration.toLong()
        )
    }
}

data class TraceStorageStats(
    val totalTraces: Int,
    val activeTraces: Int,
    val completedTraces: Int,
    val failedTraces: Int,
    val avgDurationMs: Long
)
