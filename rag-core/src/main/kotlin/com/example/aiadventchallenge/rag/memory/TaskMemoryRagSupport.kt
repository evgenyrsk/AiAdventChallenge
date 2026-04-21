package com.example.aiadventchallenge.rag.memory

object TaskMemoryRagSupport {

    fun retrievalHints(context: RagConversationContext?): List<String> {
        return context
            ?.taskState
            ?.retrievalHints()
            .orEmpty()
            .distinct()
            .take(5)
    }

    fun buildRewriteSeed(
        question: String,
        retrievalHints: List<String>
    ): String {
        return buildString {
            append(question)
            if (retrievalHints.isNotEmpty()) {
                append("\nRelevant dialog context: ")
                append(retrievalHints.joinToString("; "))
            }
        }
    }

    fun normalizeRewrittenQuery(
        question: String,
        rewriteSeed: String,
        rewrittenQuery: String?
    ): String? {
        return rewrittenQuery?.takeUnless {
            it.equals(rewriteSeed, ignoreCase = true) || it.equals(question, ignoreCase = true)
        }
    }

    fun buildEffectiveQuery(
        question: String,
        rewrittenQuery: String?,
        retrievalHints: List<String>
    ): String {
        return rewrittenQuery ?: question
    }

    fun buildPromptBlock(context: RagConversationContext?): String? {
        return context?.taskState?.toPromptBlock()
    }
}
