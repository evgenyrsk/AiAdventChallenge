package com.example.aiadventchallenge.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCommandRouterTest {
    private val router = ChatCommandRouter()

    @Test
    fun `routes bare help command`() {
        val result = router.route("/help")

        assertEquals(ChatCommand.Help(null), result)
    }

    @Test
    fun `routes help command with question`() {
        val result = router.route("/help где RAG pipeline")

        assertEquals(ChatCommand.Help("где RAG pipeline"), result)
    }

    @Test
    fun `keeps regular message in default flow`() {
        val result = router.route("Составь тренировку на грудь")

        assertTrue(result is ChatCommand.None)
    }
}
