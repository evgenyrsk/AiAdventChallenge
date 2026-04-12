package com.example.mcp.server.orchestration

import kotlinx.serialization.Serializable

@Serializable
data class CrossServerFlowContext(
    val flowId: String,
    val flowName: String,
    val steps: List<CrossServerFlowStep>,
    val startedAt: Long = System.currentTimeMillis(),
    var currentStepIndex: Int = 0,
    @kotlinx.serialization.Transient
    val stepResults: MutableMap<String, Any?> = mutableMapOf(),
    var status: FlowStatus = FlowStatus.STARTED,
    var completedAt: Long? = null,
    var errorMessage: String? = null
) {
    val totalSteps: Int get() = steps.size
    val currentStep: CrossServerFlowStep? get() = if (currentStepIndex < steps.size) steps[currentStepIndex] else null
    
    val isCompleted: Boolean get() = status == FlowStatus.COMPLETED || status == FlowStatus.FAILED
    
    val durationMs: Long get() = (completedAt ?: System.currentTimeMillis()) - startedAt
    
    fun moveToNextStep(): Boolean {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
            return true
        }
        return false
    }
    
    fun markAsCompleted() {
        status = FlowStatus.COMPLETED
        completedAt = System.currentTimeMillis()
    }
    
    fun markAsFailed(error: String) {
        status = FlowStatus.FAILED
        errorMessage = error
        completedAt = System.currentTimeMillis()
    }
    
    fun getStepResult(stepId: String): Any? = stepResults[stepId]
    
    fun setStepResult(stepId: String, result: Any?) {
        stepResults[stepId] = result
    }
}

@Serializable
data class CrossServerFlowStep(
    val stepId: String,
    val serverId: String,
    val toolName: String,
    val inputMapping: Map<String, String>,
    val outputMapping: Map<String, String>,
    val dependsOn: List<String> = emptyList(),
    val retryPolicy: RetryPolicy? = null
)

@Serializable
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val backoffMs: Long = 1000,
    val retryableErrors: List<String> = emptyList()
)

@Serializable
enum class FlowStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
