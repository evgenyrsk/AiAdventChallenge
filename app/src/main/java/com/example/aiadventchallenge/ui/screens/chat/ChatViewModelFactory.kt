package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.domain.usecase.CreateSummaryUseCase
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import com.example.aiadventchallenge.domain.context.FactExtractor

class ChatViewModelFactory(
    private val agent: ChatAgent,
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository,
    private val memoryRepository: MemoryRepository,
    private val classificationRepository: MemoryClassificationRepository,
    private val aiRequestRepository: AiRequestRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                agent,
                chatRepository,
                chatSettingsRepository,
                contextStrategyFactory,
                factRepository,
                branchRepository,
                memoryRepository,
                aiRequestRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}