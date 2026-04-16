package com.example.aiadventchallenge.domain.rag

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultQueryRewriterTest {

    private val rewriter = DefaultQueryRewriter()

    @Test
    fun `rewrite removes conversational filler and adds explicit terms`() {
        val result = rewriter.analyze("Подскажи пожалуйста, как похудеть без кардио?")
        val rewritten = result.rewrittenQuery.orEmpty()

        assertFalse(rewritten.contains("подскажи", ignoreCase = true))
        assertTrue(result.applied)
        assertTrue(rewritten.contains("дефицит калорий", ignoreCase = true))
        assertTrue(rewritten.contains("кардио", ignoreCase = true))
        assertFalse(rewritten.contains("fitness knowledge", ignoreCase = true))
    }

    @Test
    fun `rewrite adds sleep recovery intent terms`() {
        val result = rewriter.analyze("Почему сон влияет на восстановление и контроль аппетита?")

        assertTrue(result.applied)
        assertTrue(result.rewrittenQuery?.contains("качество тренировки", ignoreCase = true) == true)
        assertTrue(result.rewrittenQuery?.contains("недосып", ignoreCase = true) == true)
    }

    @Test
    fun `rewrite can stay null for already concrete query`() {
        val result = rewriter.analyze("2-4 тренировки в неделю для новичка")

        assertFalse(result.applied)
        assertNull(result.rewrittenQuery)
    }
}
