package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.TaskStateRepository
import com.example.aiadventchallenge.domain.profile.FitnessProfileManager
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.branch.BranchOrchestrator
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.usecase.CompareLocalOptimizationUseCase
import com.example.aiadventchallenge.domain.usecase.ProcessChatTurnUseCase
import com.example.aiadventchallenge.domain.usecase.RunRagEvaluationUseCase

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val branchRepository: BranchRepository,
    private val taskStateRepository: TaskStateRepository,
    private val aiRequestRepository: AiRequestRepository,
    private val fitnessProfileManager: FitnessProfileManager,
    private val chatMessageHandler: ChatMessageHandler,
    private val branchOrchestrator: BranchOrchestrator,
    private val mcpToolOrchestrator: McpToolOrchestrator,
    private val processChatTurnUseCase: ProcessChatTurnUseCase,
    private val compareLocalOptimizationUseCase: CompareLocalOptimizationUseCase,
    private val runRagEvaluationUseCase: RunRagEvaluationUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                chatRepository,
                chatSettingsRepository,
                contextStrategyFactory,
                branchRepository,
                taskStateRepository,
                aiRequestRepository,
                fitnessProfileManager,
                chatMessageHandler,
                branchOrchestrator,
                mcpToolOrchestrator,
                processChatTurnUseCase,
                compareLocalOptimizationUseCase,
                runRagEvaluationUseCase,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
