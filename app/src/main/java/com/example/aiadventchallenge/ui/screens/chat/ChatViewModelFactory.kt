package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.repository.ChatRepository

class ChatViewModelFactory(
    private val agent: ChatAgent,
    private val chatRepository: ChatRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(agent, chatRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
