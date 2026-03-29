package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.usecase.CreateSummaryUseCase
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.context.FactExtractor

class ChatViewModelFactory(
    private val agent: ChatAgent,
    private val chatRepository: ChatRepository,
    private val createSummaryUseCase: CreateSummaryUseCase,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository,
    private val factExtractor: FactExtractor
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                agent,
                chatRepository,
                createSummaryUseCase,
                chatSettingsRepository,
                contextStrategyFactory,
                factRepository,
                branchRepository,
                factExtractor
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
