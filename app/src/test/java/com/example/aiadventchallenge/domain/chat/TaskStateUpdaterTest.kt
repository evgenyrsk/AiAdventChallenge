package com.example.aiadventchallenge.domain.chat

import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import com.example.aiadventchallenge.rag.memory.TaskStateUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateUpdaterTest {

    private val updater = TaskStateUpdater()

    @Test
    fun `update captures goal constraints and clarifications`() {
        val previous = ConversationTaskState(
            dialogGoal = "Снижение веса с устойчивыми привычками"
        )

        val updated = updater.update(
            previousState = previous,
            recentMessages = listOf("Ранее обсуждали дефицит калорий"),
            newUserMessage = "Я новичок, у меня мало времени и хочу снизить вес без кардио. Что делать?"
        )

        assertEquals("Снижение веса с устойчивыми привычками", updated.dialogGoal)
        assertTrue(updated.resolvedConstraints.any { it.contains("нович", ignoreCase = true) })
        assertTrue(updated.resolvedConstraints.any { it.contains("кардио", ignoreCase = true) })
        assertTrue(updated.userClarifications.isNotEmpty())
        assertTrue(updated.openQuestions.isNotEmpty())
        assertTrue(updated.latestSummary?.contains("Снижение веса") == true)
    }
}
