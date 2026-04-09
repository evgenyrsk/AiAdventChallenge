package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.TaskRepository
import com.example.aiadventchallenge.domain.profile.FitnessProfileManager
import com.example.aiadventchallenge.domain.task.TaskIntentHandler
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.branch.BranchOrchestrator
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.task.TaskCoordinator
import com.example.aiadventchallenge.domain.usecase.mcp.CallMcpToolUseCase
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetector
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository,
    private val aiRequestRepository: AiRequestRepository,
    private val fitnessProfileManager: FitnessProfileManager,
    private val taskRepository: TaskRepository,
    private val taskIntentHandler: TaskIntentHandler,
    private val chatMessageHandler: ChatMessageHandler,
    private val branchOrchestrator: BranchOrchestrator,
    private val mcpToolOrchestrator: McpToolOrchestrator,
    private val taskCoordinator: TaskCoordinator,
    private val callMcpToolUseCase: CallMcpToolUseCase,
    private val fitnessRequestDetector: FitnessRequestDetector,
    private val nutritionRequestDetector: NutritionRequestDetector,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                chatRepository,
                chatSettingsRepository,
                contextStrategyFactory,
                factRepository,
                branchRepository,
                aiRequestRepository,
                fitnessProfileManager,
                taskRepository,
                taskIntentHandler,
                chatMessageHandler,
                branchOrchestrator,
                mcpToolOrchestrator,
                taskCoordinator,
                callMcpToolUseCase,
                fitnessRequestDetector,
                nutritionRequestDetector,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}