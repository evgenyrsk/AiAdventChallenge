package com.example.supportassistant

import com.example.supportassistant.rag.SupportRagRetriever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportRagRetrieverTest {
    private val retriever = SupportRagRetriever(
        dataDirectory = "data",
        topK = 4,
        maxContextChars = 4000
    )

    @Test
    fun `auth question retrieves auth FAQ`() {
        val chunks = retriever.retrieve("Почему не работает авторизация?")

        assertTrue(chunks.isNotEmpty())
        assertEquals("auth", chunks.first().entry.section)
        assertEquals("SUPPORT_FAQ", chunks.first().entry.scope)
    }

    @Test
    fun `local llm question retrieves local llm FAQ`() {
        val chunks = retriever.retrieve("Почему локальная модель не отвечает и Ollama недоступна?")

        assertTrue(chunks.any { it.entry.section == "local_llm" })
    }

    @Test
    fun `unrelated query returns empty retrieval gracefully`() {
        val chunks = retriever.retrieve("Сколько стоит билет на поезд до Казани?")

        assertTrue(chunks.isEmpty())
    }
}
