package com.example.aiadventchallenge.domain.validation

import android.util.Log
import com.example.aiadventchallenge.domain.model.*

interface InvariantValidator {
    suspend fun validate(
        content: String,
        context: TaskContext?,
        role: MessageRole
    ): InvariantValidationResult
}

class InvariantValidatorImpl(
    private val config: InvariantConfig
) : InvariantValidator {

    private val TAG = "INVARIANT_VIOLATION"

    override suspend fun validate(
        content: String,
        context: TaskContext?,
        role: MessageRole
    ): InvariantValidationResult {
        val violations = mutableListOf<InvariantViolation>()

        val activeInvariants = config.invariants.filter { it.isEnabled }

        for (invariant in activeInvariants) {
            val violation = invariant.validate(content, context, role)
            if (violation != null) {
                violations.add(violation)

                Log.w(TAG, "[${violation.invariantId}] ${violation.reason} | Priority: ${invariant.priority}")

                if (invariant.priority == InvariantPriority.HARD) {
                    break
                }
            }
        }

        if (violations.isEmpty()) {
            return InvariantValidationResult.Valid
        }

        violations.sortByDescending { violation ->
            if (config.invariants.find { it.id == violation.invariantId }?.priority == InvariantPriority.HARD) 1 else 0
        }

        return InvariantValidationResult.Violated(
            violations = violations,
            firstViolation = violations.first(),
            explanation = buildExplanation(violations.first())
        )
    }

    private fun buildExplanation(violation: InvariantViolation): String {
        val icon = if (violation.canProceed) "⚠️" else "🚨"

        return """
${icon} Нарушение инварианта: ${violation.invariantDescription}

Причина: ${violation.reason}

Рекомендация: ${violation.suggestion}
        """.trimIndent()
    }
}
