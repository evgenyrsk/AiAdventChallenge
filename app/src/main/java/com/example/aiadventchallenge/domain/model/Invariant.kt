package com.example.aiadventchallenge.domain.model

interface Invariant {
    val id: String
    val category: InvariantCategory
    val description: String
    val priority: InvariantPriority
    val isEnabled: Boolean

    fun validate(
        content: String,
        context: Any?,
        role: MessageRole
    ): InvariantViolation?
}

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class InvariantCategory {
    TOPIC,
    SAFETY,
    PROFESSIONALISM,
    FORMAT,
    CONTENT_QUALITY,
    TASK_FLOW
}

enum class InvariantPriority {
    HARD,
    SOFT
}

data class InvariantViolation(
    val invariantId: String,
    val invariantDescription: String,
    val reason: String,
    val suggestion: String,
    val canProceed: Boolean = false
)

sealed class InvariantValidationResult {
    object Valid : InvariantValidationResult()

    data class Violated(
        val violations: List<InvariantViolation>,
        val firstViolation: InvariantViolation,
        val explanation: String
    ) : InvariantValidationResult()
}

data class InvariantConfig(
    val invariants: List<Invariant> = emptyList()
)
