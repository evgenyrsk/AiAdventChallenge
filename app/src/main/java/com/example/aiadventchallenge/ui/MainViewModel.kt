package com.example.aiadventchallenge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.AIService
import com.example.aiadventchallenge.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val aiService = AIService()

    private val _answer = MutableStateFlow("Loading...")
    val answer: StateFlow<String> = _answer.asStateFlow()

    fun loadAnswer() {
        val messages = listOf(
            Message(
                role = "user",
                content = "Расскажи о Kotlin в одно предложение",
            ),
        )

        viewModelScope.launch {
            _answer.value = try {
//                aiService.askWithNoLimits(messages)
                aiService.askWithLimits(buildLimitedMessages(messages))
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    private fun buildLimitedMessages(messages: List<Message>): List<Message> {
        val systemMessage = Message(
            role = "system",
            content = LIMITED_SYSTEM_PROMPT
        )

        val transformed = messages.toMutableList()
        val lastUserIndex = transformed.indexOfLast { it.role == "user" }

        if (lastUserIndex >= 0) {
            val original = transformed[lastUserIndex]
            transformed[lastUserIndex] = original.copy(
                content = original.content + LIMITED_USER_SUFFIX
            )
        } else {
            transformed += Message(
                role = "user",
                content = LIMITED_USER_SUFFIX.trimIndent()
            )
        }

        return listOf(systemMessage) + transformed
    }

    companion object {
        private const val LIMITED_SYSTEM_PROMPT =
            "Ты — помощник, который отвечает строго по заданным правилам."

        private const val LIMITED_USER_SUFFIX =
            """
            
            Правила ответа:
            1. Верни ответ строго в JSON-формате.
            2. Используй только поля:
               - short_answer
               - conclusion
            3. Ответ должен быть кратким: не более 40 слов суммарно.
            4. В конце значения поля conclusion добавь маркер END.
            """
    }
}