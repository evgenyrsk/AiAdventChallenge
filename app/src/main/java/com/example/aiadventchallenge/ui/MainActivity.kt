package com.example.aiadventchallenge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.aiadventchallenge.data.local.database.AppDatabase
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.FactRepositoryImpl
import com.example.aiadventchallenge.data.repository.BranchRepositoryImpl
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.data.repository.TaskStateRepositoryImpl
import com.example.aiadventchallenge.di.AppDependencies
import com.example.aiadventchallenge.ui.screens.chat.ChatScreen
import com.example.aiadventchallenge.ui.screens.chat.ChatViewModel
import com.example.aiadventchallenge.ui.screens.chat.ChatViewModelFactory
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.context.FactExtractor
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.TaskStateRepository
import com.example.aiadventchallenge.domain.profile.FitnessProfileManager
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.chat.ChatMessageHandlerImpl
import com.example.aiadventchallenge.domain.branch.BranchOrchestrator
import com.example.aiadventchallenge.domain.branch.BranchOrchestratorImpl
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.usecase.ProcessChatTurnUseCase
import com.example.aiadventchallenge.rag.memory.TaskStateUpdater
import com.example.aiadventchallenge.ui.theme.AiAdventChallengeTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val chatRepository by lazy { ChatRepository(database.chatMessageDao(), database.branchDao(), database.factDao()) }
    private val chatSettingsRepository by lazy { AppDependencies.chatSettingsRepository }
    private val factRepository by lazy { FactRepositoryImpl(database.factDao()) }
    private val branchRepository by lazy { BranchRepositoryImpl(database.branchDao()) }
    private val taskStateRepository by lazy { TaskStateRepositoryImpl(database.conversationTaskStateDao()) }
    private val aiRequestRepository by lazy { AiRequestRepository(database.aiRequestDao()) }
    private val factExtractor by lazy { FactExtractor(AppDependencies.repository) }
    private val contextStrategyFactory by lazy { ContextStrategyFactory(factRepository, branchRepository, factExtractor, chatRepository) }
    private val fitnessProfileManager by lazy { FitnessProfileManager(chatSettingsRepository) }
    private val taskStateUpdater by lazy { TaskStateUpdater() }
    private val chatMessageHandler by lazy {
        ChatMessageHandlerImpl(
            chatRepository = chatRepository,
            agent = AppDependencies.chatAgent,
            contextStrategyFactory = contextStrategyFactory,
            chatSettingsRepository = chatSettingsRepository,
            prepareRagRequestUseCase = AppDependencies.prepareRagRequestUseCase,
            localLlmProfileResolver = AppDependencies.localLlmProfileResolver
        )
    }
    private val branchOrchestrator by lazy {
        BranchOrchestratorImpl(
            branchRepository = branchRepository,
            chatRepository = chatRepository,
            taskStateRepository = taskStateRepository
        )
    }
    private val mcpToolOrchestrator by lazy {
        AppDependencies.multiServerOrchestrator
    }
    private val processChatTurnUseCase by lazy {
        ProcessChatTurnUseCase(
            chatRepository = chatRepository,
            branchRepository = branchRepository,
            taskStateRepository = taskStateRepository,
            chatSettingsRepository = chatSettingsRepository,
            taskStateUpdater = taskStateUpdater,
            chatMessageHandler = chatMessageHandler,
            prepareRagRequestUseCase = AppDependencies.prepareRagRequestUseCase,
            localLlmProfileResolver = AppDependencies.localLlmProfileResolver
        )
    }

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
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
            AppDependencies.compareLocalOptimizationUseCase,
            AppDependencies.runRagEvaluationUseCase,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDependencies.init(this)
        enableEdgeToEdge()
        setContent {
            AiAdventChallengeTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    ChatScreen(
                        viewModel = chatViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
