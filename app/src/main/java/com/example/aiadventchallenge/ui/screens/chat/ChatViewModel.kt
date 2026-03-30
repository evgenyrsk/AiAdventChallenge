package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.domain.context.BranchingStrategy
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.model.ApiMessageDebug
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.RequestConfigDebug
import com.example.aiadventchallenge.domain.model.RequestLog
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.repository.FactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel(
    private val agent: ChatAgent,
    private val chatRepository: ChatRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository,
    private val aiRequestRepository: AiRequestRepository
) : ViewModel() {

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

    private val _debugInfo = MutableStateFlow<Map<String, Any>>(emptyMap())
    val debugInfo: StateFlow<Map<String, Any>> = _debugInfo.asStateFlow()

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
    }

    private fun loadStrategyConfig() {
        viewModelScope.launch {
            _activeStrategyConfig.value = chatSettingsRepository.getSettings()
            updateBranchingStrategyState()
        }
    }

    private fun loadBranchState() {
        viewModelScope.launch {
            branchRepository.getAllBranches().collect { branches ->
                val activeBranchId = branchRepository.getActiveBranchId()
                val branchUiModels = branches.map { branch ->
                    BranchUiModel.fromDomain(
                        id = branch.id,
                        title = branch.title,
                        isActive = branch.id == activeBranchId,
                        parentBranchId = branch.parentBranchId,
                        checkpointMessageId = branch.checkpointMessageId,
                        lastMessagePreview = null,
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
            currentConfig.type == com.example.aiadventchallenge.domain.model.ContextStrategyType.BRANCHING

        _chatUiState.value = _chatUiState.value.copy(
            isBranchingStrategy = isBranching
        )
    }

    private fun loadMessagesFromDatabase() {
        viewModelScope.launch {
            val currentConfig = chatSettingsRepository.getSettings()
            val isBranching =
                currentConfig.type == com.example.aiadventchallenge.domain.model.ContextStrategyType.BRANCHING

            if (isBranching) {
                val activeBranchId = branchRepository.getActiveBranchId()
                if (activeBranchId != null) {
                    val messages = chatRepository.getMessagesByBranch(activeBranchId)
                    _messages.value = messages
                    _chatUiState.value = _chatUiState.value.copy(messages = messages)
                } else {
                    chatRepository.getAllMessages().collect { messages ->
                        _messages.value = messages
                        _chatUiState.value = _chatUiState.value.copy(messages = messages)
                    }
                }
            } else {
                chatRepository.getAllMessages().collect { messages ->
                    _messages.value = messages
                    _chatUiState.value = _chatUiState.value.copy(messages = messages)
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

    private fun addRequestLog(log: RequestLog) {
        val updatedLogs = (listOf(log) + _requestLogs.value).take(10)
        _requestLogs.value = updatedLogs
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_isLoading.value) return

        _isLoading.value = true

        viewModelScope.launch {
            val strategyConfig = chatSettingsRepository.getSettings()
            val activeBranchId = branchRepository.getActiveBranchId() ?: "main"

            val userMessage = ChatMessage(
                id = System.currentTimeMillis().toString(),
                content = userInput,
                isFromUser = true,
                branchId = activeBranchId
            )
            _messages.value += userMessage

            val currentStrategyType = strategyConfig.type
            val isBranching =
                currentStrategyType == com.example.aiadventchallenge.domain.model.ContextStrategyType.BRANCHING

            _chatUiState.value = _chatUiState.value.copy(
                isBranchingStrategy = isBranching
            )

            val strategy = contextStrategyFactory.create(strategyConfig)

            if (strategy is BranchingStrategy) {
                strategy.initialize()
            }

            chatRepository.insertMessage(userMessage, activeBranchId)

            val messages = chatRepository.getMessagesByBranch(activeBranchId)
            val config = agent.buildRequestConfig()

            strategy.onUserMessage(userMessage)

            val apiMessages = strategy.buildContext(null, messages, config.systemPrompt)

            println("📤 API request:")
            println("  Strategy: ${strategyConfig.type}")
            println("  Messages: ${apiMessages.size}")
            println("  Debug info: ${strategy.getDebugInfo()}")

            _debugInfo.value = strategy.getDebugInfo()

            val currentLogId = System.currentTimeMillis().toString()
            val preliminaryLog = RequestLog(
                id = currentLogId,
                timestamp = System.currentTimeMillis(),
                requestConfig = RequestConfigDebug(
                    model = null,
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                    systemPrompt = config.systemPrompt
                ),
                requestMessages = apiMessages.map {
                    ApiMessageDebug(it.role, it.content)
                },
                responseContent = null,
                responseError = null,
                promptTokens = null,
                completionTokens = null,
                totalTokens = null
            )

            when (val result = agent.processRequestWithContextAndUsage(apiMessages, config)) {
                is ChatResult.Success -> {
                    val answerWithUsage = result.data

                    _lastRequestTokens.value = LastRequestTokens(
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    )

                    addRequestLog(
                        preliminaryLog.copy(
                            responseContent = answerWithUsage.content,
                            promptTokens = answerWithUsage.promptTokens,
                            completionTokens = answerWithUsage.completionTokens,
                            totalTokens = answerWithUsage.totalTokens
                        )
                    )

                    val aiMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        content = answerWithUsage.content,
                        isFromUser = false,
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    )
                    _messages.value += aiMessage

                    chatRepository.insertMessage(aiMessage, activeBranchId)

                    strategy.onAssistantMessage(aiMessage)

                    loadDialogStats()
                    loadAllTimeStats()
                }

                is ChatResult.Error -> {
                    addRequestLog(
                        preliminaryLog.copy(
                            responseError = result.message,
                            promptTokens = null,
                            completionTokens = null,
                            totalTokens = null
                        )
                    )

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
            aiRequestRepository.clearAllRequests()
            _dialogStats.value = DialogTokenStats()
            _allTimeStats.value = DialogTokenStats()
            _lastRequestTokens.value = null
            _requestLogs.value = emptyList()
        }
    }

    fun setStrategyType(type: com.example.aiadventchallenge.domain.model.ContextStrategyType) {
        viewModelScope.launch {
            chatSettingsRepository.updateStrategyType(type)
            _activeStrategyConfig.value = chatSettingsRepository.getSettings()

            val isBranching =
                type == com.example.aiadventchallenge.domain.model.ContextStrategyType.BRANCHING
            _chatUiState.value = _chatUiState.value.copy(
                isBranchingStrategy = isBranching
            )

            factRepository.clearAllFacts()
            branchRepository.clearAllBranches()

            if (isBranching) {
                val strategy = contextStrategyFactory.create(chatSettingsRepository.getSettings())
                if (strategy is BranchingStrategy) {
                    strategy.initialize()
                }
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
            branchRepository.setActiveBranchId(branchId)

            val messages = chatRepository.getMessagesByBranch(branchId)
            _messages.value = messages

            val activeBranch = branchRepository.getBranchById(branchId).first()
            _chatUiState.value = _chatUiState.value.copy(
                showBranchPicker = false,
                messages = messages,
                activeBranchId = branchId,
                activeBranchName = _chatUiState.value.availableBranches
                    .find { it.id == branchId }?.title ?: "Unknown",
                currentBranchCheckpointMessageId = activeBranch?.checkpointMessageId
            )
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

        if (state.newBranchName.isBlank()) {
            _chatUiState.value = state.copy(
                newBranchError = "Название ветки не может быть пустым"
            )
            return
        }

        val targetMessageId = state.branchCreationTargetMessageId
        if (targetMessageId == null) {
            _chatUiState.value = state.copy(
                newBranchError = "Не выбрано сообщение для создания ветки"
            )
            return
        }

        viewModelScope.launch {
            val activeBranchId = branchRepository.getActiveBranchId() ?: "main"
            val newBranchId = "branch_${System.currentTimeMillis()}"

            val newBranch = com.example.aiadventchallenge.domain.model.ChatBranch(
                id = newBranchId,
                parentBranchId = activeBranchId,
                checkpointMessageId = targetMessageId,
                title = state.newBranchName,
                createdAt = System.currentTimeMillis()
            )

            branchRepository.createBranch(newBranch)

            chatRepository.copyMessagesToBranch(
                sourceBranchId = activeBranchId,
                targetBranchId = newBranchId,
                checkpointMessageId = targetMessageId
            )

            if (switchToNew) {
                branchRepository.setActiveBranchId(newBranchId)

                val messages = chatRepository.getMessagesByBranch(newBranchId)
                _messages.value = messages

                _chatUiState.value = _chatUiState.value.copy(
                    showCreateBranchDialog = false,
                    activeBranchId = newBranchId,
                    activeBranchName = state.newBranchName,
                    messages = messages,
                    currentBranchCheckpointMessageId = targetMessageId,
                    branchCreationTargetMessageId = null,
                    branchCreationTargetPreview = null,
                    newBranchName = ""
                )
            } else {
                _chatUiState.value = _chatUiState.value.copy(
                    showCreateBranchDialog = false,
                    branchCreationTargetMessageId = null,
                    branchCreationTargetPreview = null,
                    newBranchName = ""
                )
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

            branchRepository.deleteBranch(branchId)

            if (activeBranchId == branchId) {
                val mainBranch = "main"
                branchRepository.setActiveBranchId(mainBranch)
                val messages = chatRepository.getMessagesByBranch(mainBranch)
                _messages.value = messages

                _chatUiState.value = _chatUiState.value.copy(
                    activeBranchId = mainBranch,
                    activeBranchName = "Main",
                    messages = messages,
                    currentBranchCheckpointMessageId = null
                )
            }
        }
    }

    fun onBranchIndicatorClicked(messageId: String) {
        val branches = _chatUiState.value.getBranchesForMessage(messageId)
        _chatUiState.value = _chatUiState.value.copy(
            showBranchPicker = true,
            showBranchActionsForMessageId = messageId,
            branchesForMessage = branches
        )
    }
}
