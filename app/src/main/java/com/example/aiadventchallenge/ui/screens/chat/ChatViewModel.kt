package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mapper.MessageMapper
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val agent: ChatAgent,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadMessagesFromDatabase()
    }

    private fun loadMessagesFromDatabase() {
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { messages ->
                _messages.value = messages
            }
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_isLoading.value) return

        _isLoading.value = true

        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            isFromUser = true
        )
        _messages.value += userMessage

        viewModelScope.launch {
            chatRepository.insertMessage(userMessage)

            val config = agent.buildRequestConfig()

            val validMessages = _messages.value.filter { !it.content.startsWith("Ошибка:") }
            val apiMessages = MessageMapper.mapToApiMessages(validMessages, config.systemPrompt)

            when (val result = agent.processRequestWithContext(apiMessages, config)) {
                is ChatResult.Success -> {
                    val aiMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        content = result.data,
                        isFromUser = false
                    )
                    _messages.value += aiMessage
                    chatRepository.insertMessage(aiMessage)
                }
                is ChatResult.Error -> {
                    val errorMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        content = "Ошибка: ${result.message}",
                        isFromUser = false
                    )
                    _messages.value += errorMessage
                }
            }

            _isLoading.value = false
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.deleteAllMessages()
        }
    }
}
