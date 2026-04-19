package com.example.aiadventchallenge.rag.memory

import kotlinx.serialization.Serializable

@Serializable
data class ConversationTaskState(
    val dialogGoal: String? = null,
    val resolvedConstraints: List<String> = emptyList(),
    val definedTerms: List<DefinedTerm> = emptyList(),
    val userClarifications: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val latestSummary: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toPromptBlock(): String? {
        val parts = buildList {
            dialogGoal?.takeIf { it.isNotBlank() }?.let { add("Goal: $it") }
            resolvedConstraints.takeIf { it.isNotEmpty() }?.let {
                add("Constraints: ${it.take(5).joinToString("; ")}")
            }
            userClarifications.takeIf { it.isNotEmpty() }?.let {
                add("Clarifications: ${it.takeLast(4).joinToString("; ")}")
            }
            definedTerms.takeIf { it.isNotEmpty() }?.let {
                add(
                    "Terms: ${
                        it.take(4).joinToString("; ") { term ->
                            "${term.term}=${term.meaning}"
                        }
                    }"
                )
            }
            latestSummary?.takeIf { it.isNotBlank() }?.let { add("Summary: $it") }
        }

        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    fun retrievalHints(): List<String> = buildList {
        dialogGoal?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(resolvedConstraints.take(5))
        addAll(userClarifications.takeLast(4))
        addAll(definedTerms.take(4).map { "${it.term} ${it.meaning}" })
    }
}

@Serializable
data class DefinedTerm(
    val term: String,
    val meaning: String
)

data class RagConversationContext(
    val taskState: ConversationTaskState? = null,
    val recentMessages: List<String> = emptyList()
)
