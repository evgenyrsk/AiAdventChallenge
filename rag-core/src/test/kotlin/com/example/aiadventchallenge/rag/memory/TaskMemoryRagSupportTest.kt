package com.example.aiadventchallenge.rag.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskMemoryRagSupportTest {

    @Test
    fun `support builds retrieval hints effective query and prompt block`() {
        val context = RagConversationContext(
            taskState = ConversationTaskState(
                dialogGoal = "Снижение веса",
                resolvedConstraints = listOf("Времени мало"),
                userClarifications = listOf("Нужен минимальный устойчивый режим"),
                latestSummary = "Фокус на похудении без потери мышц"
            )
        )

        val hints = TaskMemoryRagSupport.retrievalHints(context)
        val rewriteSeed = TaskMemoryRagSupport.buildRewriteSeed("А что насчет белка?", hints)
        val effective = TaskMemoryRagSupport.buildEffectiveQuery("А что насчет белка?", null, hints)
        val promptBlock = TaskMemoryRagSupport.buildPromptBlock(context)

        assertTrue(hints.isNotEmpty())
        assertTrue(rewriteSeed.contains("Relevant dialog context"))
        assertTrue(effective.contains("dialog context"))
        assertTrue(promptBlock?.contains("Goal: Снижение веса") == true)
    }
}
