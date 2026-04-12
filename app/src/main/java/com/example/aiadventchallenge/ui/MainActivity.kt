package com.example.aiadventchallenge.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.aiadventchallenge.data.local.database.AppDatabase
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.ChatSettingsRepository as DataChatSettingsRepository
import com.example.aiadventchallenge.data.repository.FactRepositoryImpl
import com.example.aiadventchallenge.data.repository.BranchRepositoryImpl
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.data.repository.MemoryRepositoryImpl
import com.example.aiadventchallenge.data.repository.MemoryClassificationRepositoryImpl
import com.example.aiadventchallenge.data.repository.TaskRepositoryImpl
import com.example.aiadventchallenge.di.AppDependencies
import com.example.aiadventchallenge.ui.screens.chat.ChatScreen
import com.example.aiadventchallenge.ui.screens.chat.ChatViewModel
import com.example.aiadventchallenge.ui.screens.chat.ChatViewModelFactory
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonScreen
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonViewModel
import com.example.aiadventchallenge.ui.screens.promptcomparison.PromptComparisonViewModelFactory
import com.example.aiadventchallenge.ui.screens.temperature.TemperatureScreen
import com.example.aiadventchallenge.ui.screens.temperature.TemperatureViewModel
import com.example.aiadventchallenge.ui.screens.temperature.TemperatureViewModelFactory
import com.example.aiadventchallenge.ui.screens.modelversions.ModelVersionsScreen
import com.example.aiadventchallenge.ui.screens.modelversions.ModelVersionsViewModel
import com.example.aiadventchallenge.ui.screens.modelversions.ModelVersionsViewModelFactory
import com.example.aiadventchallenge.data.export.ModelResultsExporter
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.context.FactExtractor
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import com.example.aiadventchallenge.domain.repository.TaskRepository
import com.example.aiadventchallenge.domain.profile.FitnessProfileManager
import com.example.aiadventchallenge.domain.parser.UserResponseParser
import com.example.aiadventchallenge.domain.task.TaskIntentHandler
import com.example.aiadventchallenge.domain.task.TaskIntentHandlerImpl
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.chat.ChatMessageHandlerImpl
import com.example.aiadventchallenge.domain.branch.BranchOrchestrator
import com.example.aiadventchallenge.domain.branch.BranchOrchestratorImpl
import com.example.aiadventchallenge.domain.detector.FitnessRequestDetectorImpl
import com.example.aiadventchallenge.domain.detector.CrossServerFlowDetector
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestratorImpl
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetector
import com.example.aiadventchallenge.domain.detector.NutritionRequestDetectorImpl
import com.example.aiadventchallenge.domain.task.TaskCoordinator
import com.example.aiadventchallenge.domain.task.TaskCoordinatorImpl
import java.io.File
import com.example.aiadventchallenge.ui.theme.AiAdventChallengeTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getDatabase(this) }
    private val chatRepository by lazy { ChatRepository(database.chatMessageDao(), database.branchDao(), database.factDao()) }
    private val chatSettingsRepository by lazy { DataChatSettingsRepository(database.chatSettingsDao()) }
    private val factRepository by lazy { FactRepositoryImpl(database.factDao()) }
    private val branchRepository by lazy { BranchRepositoryImpl(database.branchDao()) }
    private val memoryRepository by lazy { MemoryRepositoryImpl(database.memoryEntriesDao()) }
    private val memoryClassificationRepository by lazy { MemoryClassificationRepositoryImpl(database.memoryClassificationDao()) }
    private val aiRequestRepository by lazy { AiRequestRepository(database.aiRequestDao()) }
    private val taskRepository by lazy { TaskRepositoryImpl(database.taskDao()) }
    private val factExtractor by lazy { FactExtractor(AppDependencies.repository) }
    private val contextStrategyFactory by lazy { ContextStrategyFactory(factRepository, branchRepository, factExtractor, chatRepository, memoryRepository, AppDependencies.repository, memoryClassificationRepository) }
    private val fitnessProfileManager by lazy { FitnessProfileManager(chatSettingsRepository) }
    private val taskIntentHandler by lazy { TaskIntentHandlerImpl(taskRepository, AppDependencies.userResponseParser) }
    private val chatMessageHandler by lazy {
        ChatMessageHandlerImpl(
            chatRepository = chatRepository,
            agent = AppDependencies.chatAgent,
            contextStrategyFactory = contextStrategyFactory,
            chatSettingsRepository = chatSettingsRepository
        )
    }
    private val branchOrchestrator by lazy {
        BranchOrchestratorImpl(
            branchRepository = branchRepository,
            chatRepository = chatRepository
        )
    }
    private val nutritionRequestDetector by lazy { NutritionRequestDetectorImpl() }
    private val fitnessRequestDetector by lazy { FitnessRequestDetectorImpl() }
    private val mcpToolOrchestrator by lazy {
        McpToolOrchestratorImpl(
            callMcpToolUseCase = AppDependencies.callMcpToolUseCase,
            nutritionRequestDetector = nutritionRequestDetector,
            fitnessRequestDetector = fitnessRequestDetector,
            crossServerFlowDetector = CrossServerFlowDetector
        )
    }
    private val taskCoordinator by lazy {
        TaskCoordinatorImpl(
            taskRepository = taskRepository
        )
    }

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
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
            AppDependencies.callMcpToolUseCase,
            fitnessRequestDetector,
            nutritionRequestDetector,
        )
    }

    private val promptComparisonViewModel: PromptComparisonViewModel by viewModels {
        PromptComparisonViewModelFactory(
            AppDependencies.askWithPromptModeUseCase,
            AppDependencies.compareResultsUseCase,
        )
    }

    private val temperatureViewModel: TemperatureViewModel by viewModels {
        TemperatureViewModelFactory(
            AppDependencies.temperatureUseCase,
            AppDependencies.compareTemperatureResultsUseCase
        )
    }

    private val outputDir by lazy {
        android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
    }

    private val modelVersionsViewModel: ModelVersionsViewModel by viewModels {
        ModelVersionsViewModelFactory(
            AppDependencies.askModelUseCase,
            ModelResultsExporter(outputDir),
            AppDependencies.repository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppDependencies.init(this)
        enableEdgeToEdge()
        setContent {
            AiAdventChallengeTheme {
                var selectedTab by remember { mutableIntStateOf(0) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = "Чат"
                                    )
                                },
                                label = { Text("Чат") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = {
                                    Icon(
                                        Icons.Default.Compare,
                                        contentDescription = "Сравнение"
                                    )
                                },
                                label = { Text("Сравнение") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = {
                                    Icon(
                                        Icons.Default.Thermostat,
                                        contentDescription = "Temperature"
                                    )
                                },
                                label = { Text("Temperature") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = {
                                    Icon(
                                        Icons.Default.ModelTraining,
                                        contentDescription = "Модели"
                                    )
                                },
                                label = { Text("Модели") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (selectedTab) {
                        0 -> ChatScreen(
                            viewModel = chatViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )

                        1 -> PromptComparisonScreen(
                            viewModel = promptComparisonViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )

                        2 -> TemperatureScreen(
                            viewModel = temperatureViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )

                        3 -> ModelVersionsScreen(
                            viewModel = modelVersionsViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
