package com.example.aiadventchallenge.ui.screens.chat

import android.util.Log
import java.util.UUID
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.RequestLog
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.profile.FitnessProfileManager
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.chat.ChatMessageResult
import com.example.aiadventchallenge.domain.branch.BranchOrchestrator
import com.example.aiadventchallenge.domain.branch.BranchCreationResult
import com.example.aiadventchallenge.domain.branch.BranchSwitchResult
import com.example.aiadventchallenge.domain.branch.BranchCreationErrorType
import com.example.aiadventchallenge.domain.mcp.McpToolOrchestrator
import com.example.aiadventchallenge.domain.mcp.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val branchRepository: BranchRepository,
    private val aiRequestRepository: AiRequestRepository,
    private val fitnessProfileManager: FitnessProfileManager,
    private val chatMessageHandler: ChatMessageHandler,
    private val branchOrchestrator: BranchOrchestrator,
    private val mcpToolOrchestrator: McpToolOrchestrator
) : ViewModel() {

    private val TAG = "ChatViewModel"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastRequestTokens = MutableStateFlow<LastRequestTokens?>(null)
    val lastRequestTokens: StateFlow<LastRequestTokens?> = _lastRequestTokens.asStateFlow()

    private val _dialogStats = MutableStateFlow(DialogTokenStats())
    val dialogStats: StateFlow<DialogTokenStats> = _dialogStats.asStateFlow()

    private val _allTimeStats = MutableStateFlow(DialogTokenStats())
    val allTimeStats: StateFlow<DialogTokenStats> = _allTimeStats.asStateFlow()

    private val _requestLogs = MutableStateFlow<List<RequestLog>>(emptyList())
    val requestLogs: StateFlow<List<RequestLog>> = _requestLogs.asStateFlow()

    private val _activeStrategyConfig = MutableStateFlow<ContextStrategyConfig?>(null)
    val activeStrategyConfig: StateFlow<ContextStrategyConfig?> =
        _activeStrategyConfig.asStateFlow()

    private val _chatUiState = MutableStateFlow(ChatUiState())
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    data class LastRequestTokens(
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?
    )

    init {
        loadMessagesFromDatabase()
        loadDialogStats()
        loadAllTimeStats()
        loadStrategyConfig()
        loadBranchState()
        loadFitnessProfile()
    }

    private fun loadStrategyConfig() {
        viewModelScope.launch {
            _activeStrategyConfig.value = chatSettingsRepository.getSettings()
            updateBranchingStrategyState()
        }
    }

    private fun loadFitnessProfile() {
        viewModelScope.launch {
            val profile = fitnessProfileManager.getActiveProfile()
            _chatUiState.value = _chatUiState.value.copy(
                fitnessProfile = profile
            )
        }
    }

    private fun loadBranchState() {
        viewModelScope.launch {
            branchRepository.getAllBranches().collect { branches ->
                val activeBranchId = branchRepository.getActiveBranchId()
                val branchUiModels = branches.map { branch ->
                    val lastMessagePreview = branch.lastMessageId?.let { messageId ->
                        chatRepository.getMessageById(messageId)?.content?.take(50)?.let { "$it..." }
                    }

                    BranchUiModel.fromDomain(
                        id = branch.id,
                        title = branch.title,
                        isActive = branch.id == activeBranchId,
                        parentBranchId = branch.parentBranchId,
                        checkpointMessageId = branch.checkpointMessageId,
                        lastMessageId = branch.lastMessageId,
                        lastMessagePreview = lastMessagePreview,
                        updatedAt = branch.createdAt
                    )
                }

                val currentConfig = chatSettingsRepository.getSettings()
                val isBranching =
                    currentConfig.type == com.example.aiadventchallenge.domain.model.ContextStrategyType.BRANCHING

                val activeBranch = if (activeBranchId != null) {
                    branchRepository.getBranchById(activeBranchId).first()
                } else {
                    null
                }

                _chatUiState.value = _chatUiState.value.copy(
                    isBranchingStrategy = isBranching,
                    activeBranchId = activeBranchId,
                    activeBranchName = branchUiModels.find { it.isActive }?.title ?: "Main",
                    availableBranches = branchUiModels,
                    currentBranchCheckpointMessageId = activeBranch?.checkpointMessageId
                )
            }
        }
    }

    private fun updateBranchingStrategyState() {
        val currentConfig = _activeStrategyConfig.value ?: return
        val isBranching =
            currentConfig.type == ContextStrategyType.BRANCHING

        _chatUiState.value = _chatUiState.value.copy(
            isBranchingStrategy = isBranching
        )
    }

    private fun loadMessagesFromDatabase() {
        viewModelScope.launch {
            val currentConfig = chatSettingsRepository.getSettings()
            val isBranching =
                currentConfig.type == ContextStrategyType.BRANCHING

            if (isBranching) {
                val activeBranchId = branchRepository.getActiveBranchId()
                if (activeBranchId != null) {
                    val messages = chatRepository.getBranchPathWithCheckpoint(activeBranchId)
                    _messages.value = messages
                } else {
                    chatRepository.getAllMessages().collect { messages ->
                        _messages.value = messages
                    }
                }
            } else {
                chatRepository.getAllMessages().collect { messages ->
                    _messages.value = messages
                }
            }
        }
    }

    private fun loadDialogStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _dialogStats.value = chatRepository.getDialogStats()
        }
    }

    private fun loadAllTimeStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _allTimeStats.value = aiRequestRepository.getAllTimeStats()
        }
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_isLoading.value) return

        _isLoading.value = true

        viewModelScope.launch {
            val activeBranchId = branchRepository.getActiveBranchId() ?: "main"
            val parentMessageId = if (_messages.value.isNotEmpty()) _messages.value.last().id else null

            val mcpToolResult = mcpToolOrchestrator.detectAndExecuteTool(userInput)
            val mcpContext = when (mcpToolResult) {
                is ToolExecutionResult.Success -> mcpToolResult.context
                is ToolExecutionResult.NoToolFound -> null
                is ToolExecutionResult.Error -> {
                    Log.e(TAG, "❌ MCP tool error: ${mcpToolResult.message}")
                    null
                }
            }

            val result = chatMessageHandler.handleUserMessage(
                userInput = userInput,
                fitnessProfile = _chatUiState.value.fitnessProfile,
                activeBranchId = activeBranchId,
                parentMessageId = parentMessageId,
                mcpContext = mcpContext
            )

            when (result) {
                is ChatMessageResult.Success -> {
                    _isLoading.value = false
                }
                is ChatMessageResult.Error -> {
                    addSystemMessage("Ошибка: ${result.errorMessage}")
                    _isLoading.value = false
                }
                is ChatMessageResult.EmptyResponse -> {
                    addSystemMessage(result.errorMessage)
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun addSystemMessage(content: String, parentMessageId: String? = null) {
        val parentId = parentMessageId ?: _messages.value.lastOrNull()?.id
        val activeBranchId = branchRepository.getActiveBranchId() ?: "main"

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            parentMessageId = parentId,
            content = content,
            isFromUser = false,
            isSystemMessage = true,
            branchId = activeBranchId
        )

        chatRepository.insertMessage(message, activeBranchId, parentId)
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.deleteAllMessages()
            aiRequestRepository.clearAllRequests()
            _dialogStats.value = DialogTokenStats()
            _allTimeStats.value = DialogTokenStats()
            _lastRequestTokens.value = null
            _requestLogs.value = emptyList()

            _messages.value = emptyList()
            _chatUiState.value = _chatUiState.value.copy(
                activeBranchId = null,
                activeBranchName = null,
                currentBranchCheckpointMessageId = null
            )
        }
    }

    fun setStrategyType(type: ContextStrategyType) {
        viewModelScope.launch {
            chatSettingsRepository.updateStrategyType(type)
            _activeStrategyConfig.value = chatSettingsRepository.getSettings()

            val isBranching = type == ContextStrategyType.BRANCHING
            _chatUiState.value = _chatUiState.value.copy(
                isBranchingStrategy = isBranching,
                activeBranchId = null,
                activeBranchName = null,
                currentBranchCheckpointMessageId = null,
                availableBranches = emptyList()
            )

            branchRepository.clearAllBranches()
            chatRepository.deleteMessagesByBranch("main")

            _messages.value = emptyList()

            if (isBranching) {
                contextStrategyFactory.create(chatSettingsRepository.getSettings())
            }
        }
    }

    fun setWindowSize(windowSize: Int) {
        viewModelScope.launch {
            chatSettingsRepository.updateWindowSize(windowSize)
            _activeStrategyConfig.value = chatSettingsRepository.getSettings()
        }
    }

    fun onBranchChipClicked() {
        _chatUiState.value = _chatUiState.value.copy(
            showBranchPicker = true
        )
    }

    fun onBranchPickerDismiss() {
        _chatUiState.value = _chatUiState.value.copy(
            showBranchPicker = false
        )
    }

    fun onBranchSelected(branchId: String) {
        viewModelScope.launch {
            val result = branchOrchestrator.switchToBranch(branchId)

            when (result) {
                is BranchSwitchResult.Success -> {
                    _chatUiState.value = _chatUiState.value.copy(
                        showBranchPicker = false,
                        activeBranchId = branchId,
                        activeBranchName = result.branchName,
                        currentBranchCheckpointMessageId = result.checkpointMessageId
                    )
                }
                is BranchSwitchResult.Error -> {
                }
            }
        }
    }

    fun onCreateBranchFromMessage(messageId: String) {
        val message = _messages.value.find { it.id == messageId }
        val preview = message?.content?.take(50)?.let { "$it..." } ?: "Сообщение"

        val branchNumber = _chatUiState.value.availableBranches.size + 1

        _chatUiState.value = _chatUiState.value.copy(
            showCreateBranchDialog = true,
            branchCreationTargetMessageId = messageId,
            branchCreationTargetPreview = preview,
            newBranchName = "Ветка $branchNumber",
            newBranchError = null
        )
    }

    fun onNewBranchNameChanged(name: String) {
        _chatUiState.value = _chatUiState.value.copy(
            newBranchName = name,
            newBranchError = null
        )
    }

    fun onCreateBranchConfirmed(switchToNew: Boolean = true) {
        val state = _chatUiState.value
        val targetMessageId = state.branchCreationTargetMessageId

        viewModelScope.launch {
            val result = branchOrchestrator.createBranchFromMessage(
                messageId = targetMessageId ?: "",
                branchName = state.newBranchName,
                switchToNew = switchToNew
            )

            when (result) {
                is BranchCreationResult.Success -> {
                    _chatUiState.value = _chatUiState.value.copy(
                        showCreateBranchDialog = false,
                        activeBranchId = result.branchId,
                        activeBranchName = state.newBranchName,
                        currentBranchCheckpointMessageId = result.checkpointMessageId,
                        branchCreationTargetMessageId = null,
                        branchCreationTargetPreview = null,
                        newBranchName = ""
                    )
                }
                is BranchCreationResult.Error -> {
                    when (result.type) {
                        BranchCreationErrorType.EMPTY_NAME -> {
                            _chatUiState.value = _chatUiState.value.copy(
                                newBranchError = result.message
                            )
                        }
                        BranchCreationErrorType.NO_MESSAGE_SELECTED -> {
                            _chatUiState.value = _chatUiState.value.copy(
                                newBranchError = result.message
                            )
                        }
                        BranchCreationErrorType.UNKNOWN -> {
                            _chatUiState.value = _chatUiState.value.copy(
                                showCreateBranchDialog = false,
                                branchCreationTargetMessageId = null,
                                branchCreationTargetPreview = null,
                                newBranchName = ""
                            )
                        }
                    }
                }
            }
        }
    }

    fun onCreateBranchDialogDismiss() {
        _chatUiState.value = _chatUiState.value.copy(
            showCreateBranchDialog = false,
            branchCreationTargetMessageId = null,
            branchCreationTargetPreview = null,
            newBranchName = "",
            newBranchError = null
        )
    }

    fun onDeleteBranch(branchId: String) {
        viewModelScope.launch {
            val activeBranchId = branchRepository.getActiveBranchId()

            branchOrchestrator.deleteBranch(branchId)

            if (activeBranchId == branchId) {
                val result = branchOrchestrator.switchToBranch("main")

                when (result) {
                    is BranchSwitchResult.Success -> {
                        _chatUiState.value = _chatUiState.value.copy(
                            activeBranchId = "main",
                            activeBranchName = "Main",
                            currentBranchCheckpointMessageId = result.checkpointMessageId
                        )
                    }
                    is BranchSwitchResult.Error -> {
                    }
                }
            }
        }
    }

    fun onBranchIndicatorClicked(messageId: String) {
        val branches = branchOrchestrator.getBranchesForMessage(
            messageId = messageId,
            allBranches = _chatUiState.value.availableBranches
        )
        _chatUiState.value = _chatUiState.value.copy(
            showBranchPicker = true,
            showBranchActionsForMessageId = messageId,
            branchesForMessage = branches
        )
    }

    fun setFitnessProfile(profile: FitnessProfileType) {
        viewModelScope.launch {
            fitnessProfileManager.setActiveProfile(profile)
            _chatUiState.value = _chatUiState.value.copy(
                fitnessProfile = profile
            )
        }
    }
}
