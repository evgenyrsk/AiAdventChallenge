package com.example.aiadventchallenge.rag.rewrite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FitnessQueryRewriteEngineTest {

    @Test
    fun `sleep question expands into recovery intent query`() {
        val result = FitnessQueryRewriteEngine.analyze(
            "Подскажи пожалуйста, почему сон влияет на восстановление и контроль аппетита?"
        )

        assertTrue(result.applied)
        assertEquals(RewriteIntent.SLEEP_RECOVERY_APPETITE, result.detectedIntent)
        assertTrue(result.rewrittenQuery?.contains("качество тренировки") == true)
        assertFalse(result.rewrittenQuery?.contains("fitness knowledge", ignoreCase = true) == true)
    }

    @Test
    fun `already concrete beginner frequency query is not rewritten`() {
        val result = FitnessQueryRewriteEngine.analyze("2-4 тренировки в неделю для новичка")

        assertFalse(result.applied)
        assertNull(result.rewrittenQuery)
    }
}
