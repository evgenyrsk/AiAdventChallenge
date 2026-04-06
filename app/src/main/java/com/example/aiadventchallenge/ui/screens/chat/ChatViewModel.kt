package com.example.aiadventchallenge.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.data.repository.AiRequestRepository
import com.example.aiadventchallenge.domain.context.BranchingStrategy
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.model.ChatBranch
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.InvariantValidationResult
import com.example.aiadventchallenge.domain.model.MessageRole
import com.example.aiadventchallenge.domain.model.RequestLog
import com.example.aiadventchallenge.domain.model.TaskAction
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.model.TaskStateMachine
import com.example.aiadventchallenge.domain.model.TransitionResult
import com.example.aiadventchallenge.data.config.TaskIntent
import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.data.config.TaskPromptBuilder
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.TaskRepository
import com.example.aiadventchallenge.domain.profile.FitnessProfileManager
import com.example.aiadventchallenge.domain.validation.InvariantValidator
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
    private val memoryRepository: MemoryRepository,
    private val aiRequestRepository: AiRequestRepository,
    private val fitnessProfileManager: FitnessProfileManager,
    private val taskRepository: TaskRepository,
    private val invariantValidator: InvariantValidator
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

    private val _taskContext = MutableStateFlow<TaskContext?>(null)
    val taskContext: StateFlow<TaskContext?> = _taskContext.asStateFlow()

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
        loadActiveTask()
    }

    private fun loadActiveTask() {
        viewModelScope.launch {
            val task = taskRepository.getActiveTask()
            _taskContext.value = task
            if (task != null) {
                Log.d(TAG, "=== Active Task Loaded ===")
                Log.d(TAG, "Task ID: ${task.taskId}")
                Log.d(TAG, "Query: ${task.query}")
                Log.d(TAG, "Phase: ${task.phase.label} (step ${task.currentStep}/${task.totalSteps})")
                Log.d(TAG, "isActive: ${task.isActive}")
                Log.d(TAG, "AwaitingConfirmation: ${task.awaitingUserConfirmation}")
                Log.d(TAG, "Progress: ${(task.progress * 100).toInt()}%")
                Log.d(TAG, "=== Active Task Loaded End ===\n")
            } else {
                Log.d(TAG, "No active task found")
            }
        }
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
                    val messages = chatRepository.getBranchPathWithCheckpoint(activeBranchId)
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

    private fun logLlmRequest(
        userInput: String,
        phase: TaskPhase?,
        query: String?
    ) {
        Log.d(TAG, "📤 === OUTGOING LLM REQUEST ===")
        Log.d(TAG, "   User input: $userInput")
        Log.d(TAG, "   Phase: ${phase?.label}")
        Log.d(TAG, "   Active task: $query")
        Log.d(TAG, "   ============================")
    }

    private fun logLlmResponse(
        aiResponse: EnhancedTaskAiResponse,
        totalTokens: Int,
        contentLength: Int
    ) {
        Log.d(TAG, "📥 === INCOMING LLM RESPONSE ===")
        Log.d(TAG, "   Raw content length: $contentLength")
        Log.d(TAG, "   Intent: ${aiResponse.taskIntent}")
        Log.d(TAG, "   step_completed: ${aiResponse.stepCompleted}")
        Log.d(TAG, "   plan_ready: ${aiResponse.planReady}")
        Log.d(TAG, "   transitionTo: ${aiResponse.transitionTo}")
        Log.d(TAG, "   taskCompleted: ${aiResponse.taskCompleted}")
        Log.d(TAG, "   nextAction: ${aiResponse.nextAction}")
        Log.d(TAG, "   Tokens: $totalTokens")

        // Проверка на пустой результат
        if (aiResponse.result.isEmpty()) {
            Log.w(TAG, "⚠️ Empty result detected - will show error to user")
        } else {
            Log.d(TAG, "   Response preview: ${aiResponse.result.take(150)}...")
        }

        Log.d(TAG, "   ===============================")
    }

    fun sendMessage(userInput: String) {
        if (userInput.isBlank()) return
        if (_isLoading.value) return

        _isLoading.value = true

        viewModelScope.launch {
            val activeTask = _taskContext.value

            if (activeTask != null && activeTask.isActive && activeTask.phase != TaskPhase.DONE) {
                processIntelligentMessage(userInput)
            } else {
                processNormalMessage(userInput)
            }
        }
    }

    private fun processIntelligentMessage(userInput: String) {
        viewModelScope.launch {
            val strategyConfig = chatSettingsRepository.getSettings()
            val activeBranchId = branchRepository.getActiveBranchId() ?: "main"

            val parentMessageId = if (_messages.value.isNotEmpty()) _messages.value.last().id else null

            val userMessage = ChatMessage(
                id = System.currentTimeMillis().toString(),
                parentMessageId = parentMessageId,
                content = userInput,
                isFromUser = true,
                branchId = activeBranchId
            )
            _messages.value += userMessage

            chatRepository.insertMessage(userMessage, activeBranchId, parentMessageId)

            val activeMessages = chatRepository.getMessagesByBranch(activeBranchId)
            val config = agent.buildRequestConfigWithTask(
                taskContext = _taskContext.value,
                fitnessProfile = _chatUiState.value.fitnessProfile,
                userInput = userInput
            )

            logLlmRequest(userInput, _taskContext.value?.phase, _taskContext.value?.query)

            val strategy = contextStrategyFactory.create(strategyConfig)
            val apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)

            when (val result = agent.processRequestWithContextAndUsage(
                messages = apiMessages,
                config = config,
                userInput = userInput,
                taskContext = _taskContext.value
            )) {
                is ChatResult.Success -> {
                    val answerWithUsage = result.data

                    val aiResponse = TaskPromptBuilder.parseEnhancedAiResponse(answerWithUsage.content)

                    logLlmResponse(aiResponse, answerWithUsage.totalTokens ?: 0, answerWithUsage.content.length)

                    // Обработка пустого результата
                    if (aiResponse.result.isEmpty()) {
                        val errorMessage = ChatMessage(
                            id = (System.currentTimeMillis() + 1).toString(),
                            parentMessageId = userMessage.id,
                            content = "❌ Не удалось получить ответ от ассистента. Попробуйте переформулировать запрос.",
                            isFromUser = false,
                            isSystemMessage = true,
                            branchId = activeBranchId
                        )
                        _messages.value += errorMessage
                        chatRepository.insertMessage(errorMessage, activeBranchId, errorMessage.parentMessageId)

                        _isLoading.value = false
                        return@launch
                    }

                    val aiMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        parentMessageId = userMessage.id,
                        content = aiResponse.result,
                        isFromUser = false,
                        branchId = activeBranchId,
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    )
                    _messages.value += aiMessage
                    chatRepository.insertMessage(aiMessage, activeBranchId, aiMessage.parentMessageId)

                    _isLoading.value = false

                    handleTaskIntent(aiResponse, userInput, activeBranchId)

                    strategy.onConversationPair(userMessage, aiMessage)
                }
                is ChatResult.Error -> {
                    val errorMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        parentMessageId = userMessage.id,
                        content = "Ошибка: ${result.message}",
                        isFromUser = false,
                        isSystemMessage = true,
                        branchId = activeBranchId
                    )
                    _messages.value += errorMessage
                    _isLoading.value = false
                }
            }
        }
    }

    private fun processNormalMessage(userInput: String) {
        viewModelScope.launch {
            val strategyConfig = chatSettingsRepository.getSettings()
            val activeBranchId = branchRepository.getActiveBranchId() ?: "main"

            // ========== НОВАЯ ЛОГИКА: Предварительное создание задачи ==========
            if (_taskContext.value == null) {
                val profile = _chatUiState.value.fitnessProfile
                val task = taskRepository.createTask(userInput, profile)
                _taskContext.value = task
                
                Log.d(TAG, "Task created in advance before LLM request")
                Log.d(TAG, "  Task ID: ${task.taskId}")
                Log.d(TAG, "  Query: ${task.query}")
                Log.d(TAG, "  Phase: ${task.phase.label} (${task.currentStep}/${task.totalSteps})")
            }
            // ================================================================

            val parentMessageId = if (_messages.value.isNotEmpty()) _messages.value.last().id else null

            val userMessage = ChatMessage(
                id = System.currentTimeMillis().toString(),
                parentMessageId = parentMessageId,
                content = userInput,
                isFromUser = true,
                branchId = activeBranchId
            )
            _messages.value += userMessage

            chatRepository.insertMessage(userMessage, activeBranchId, parentMessageId)

            val activeMessages = chatRepository.getMessagesByBranch(activeBranchId)
            val config = agent.buildRequestConfigWithTask(
                taskContext = _taskContext.value,
                fitnessProfile = _chatUiState.value.fitnessProfile,
                userInput = userInput
            )

            logLlmRequest(userInput, _taskContext.value?.phase, _taskContext.value?.query)

            val strategy = contextStrategyFactory.create(strategyConfig)
            val apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)

            when (val result = agent.processRequestWithContextAndUsage(
                messages = apiMessages,
                config = config,
                userInput = userInput,
                taskContext = _taskContext.value
            )) {
                is ChatResult.Success -> {
                    val answerWithUsage = result.data

                    val aiResponse = TaskPromptBuilder.parseEnhancedAiResponse(answerWithUsage.content)

                    logLlmResponse(aiResponse, answerWithUsage.totalTokens ?: 0, answerWithUsage.content.length)

                    // Обработка пустого результата
                    if (aiResponse.result.isEmpty()) {
                        val errorMessage = ChatMessage(
                            id = (System.currentTimeMillis() + 1).toString(),
                            parentMessageId = userMessage.id,
                            content = "❌ Не удалось получить ответ от ассистента. Попробуйте переформулировать запрос.",
                            isFromUser = false,
                            isSystemMessage = true,
                            branchId = activeBranchId
                        )
                        _messages.value += errorMessage
                        chatRepository.insertMessage(errorMessage, activeBranchId, errorMessage.parentMessageId)

                        _isLoading.value = false
                        return@launch
                    }

                    val aiMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        parentMessageId = userMessage.id,
                        content = aiResponse.result,
                        isFromUser = false,
                        branchId = activeBranchId,
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    )
                    _messages.value += aiMessage
                    chatRepository.insertMessage(aiMessage, activeBranchId, aiMessage.parentMessageId)

                    _isLoading.value = false

                    handleTaskIntent(aiResponse, userInput, activeBranchId)

                    strategy.onConversationPair(userMessage, aiMessage)
                }
                is ChatResult.Error -> {
                    val errorMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        parentMessageId = userMessage.id,
                        content = "Ошибка: ${result.message}",
                        isFromUser = false,
                        isSystemMessage = true,
                        branchId = activeBranchId
                    )
                    _messages.value += errorMessage
                    _isLoading.value = false
                }
            }
        }
    }

    private fun handleTaskIntent(aiResponse: EnhancedTaskAiResponse, userInput: String, activeBranchId: String) {
        Log.d(TAG, "=== Task Intent Handler ===")
        Log.d(TAG, "Intent: ${aiResponse.taskIntent}")
        Log.d(TAG, "stepCompleted: ${aiResponse.stepCompleted}")
        Log.d(TAG, "taskCompleted: ${aiResponse.taskCompleted}")
        Log.d(TAG, "transitionTo: ${aiResponse.transitionTo}")
        Log.d(TAG, "nextAction: ${aiResponse.nextAction}")

        val currentTask = _taskContext.value
        if (currentTask != null) {
            Log.d(TAG, "Current task:")
            Log.d(TAG, "  Phase: ${currentTask.phase.label} (${currentTask.currentStep}/${currentTask.totalSteps})")
            Log.d(TAG, "  Query: ${currentTask.query}")
            Log.d(TAG, "  isActive: ${currentTask.isActive}")
            Log.d(TAG, "  awaitingConfirmation: ${currentTask.awaitingUserConfirmation}")
        } else {
            Log.d(TAG, "No active task")
        }
        Log.d(TAG, "Has active task for intent processing: ${currentTask != null}")

        when (aiResponse.taskIntent) {
            TaskIntent.NEW_TASK -> {
                Log.d(TAG, "Action: Creating new task")
                val taskQuery = aiResponse.newTaskQuery ?: userInput

                // ========== НОВАЯ ПРОВЕРКА: Защита от дублирования ==========
                if (currentTask != null) {
                    Log.w(TAG, "LLM returned NEW_TASK but task already exists (ID: ${currentTask.taskId})")
                    Log.w(TAG, "  Expected: CONTINUE_TASK, Actual: NEW_TASK")
                    Log.w(TAG, "  Current task query: ${currentTask.query}")
                    Log.w(TAG, "  New task query: $taskQuery")
                    Log.w(TAG, "  Skipping task creation to avoid duplicate")
                    // Не создаём дубликат задачи
                } else {
                    createTask(taskQuery)
                }
                // ================================================================
            }
            TaskIntent.SWITCH_TASK -> {
                Log.d(TAG, "Action: Switching task")
                pauseTask()
                val taskQuery = aiResponse.newTaskQuery ?: userInput
                createTask(taskQuery)
            }
            TaskIntent.PAUSE_TASK -> {
                Log.d(TAG, "Action: Pausing task")
                pauseTask()
            }
            TaskIntent.CONTINUE_TASK,
            TaskIntent.CLARIFICATION -> {
                if (currentTask != null) {
                    Log.d(TAG, "Action: Processing task continuation/clarification")
                    Log.d(TAG, "Has active task for intent processing: true")
                    Log.d(TAG, "Response: stepCompleted=${aiResponse.stepCompleted}, transitionTo=${aiResponse.transitionTo}")

                    // ПРИОРИТЕТ 1: Обработка подтверждения пользователя
                    if (currentTask.awaitingUserConfirmation) {
                        val isAffirmative = parseAffirmativeResponse(userInput)
                        if (isAffirmative) {
                            Log.d(TAG, "User confirmed - transitioning to next phase")
                            val nextPhase = TaskStateMachine().getNextPhase(currentTask.phase)
                            if (nextPhase != null) {
                                transitionTaskTo(nextPhase)
                            } else if (currentTask.phase != TaskPhase.DONE) {
                                // Нет следующей фазы - финальная, переходим в DONE
                                Log.d(TAG, "No next phase, transitioning to DONE")
                                transitionTaskTo(TaskPhase.DONE)
                            }
                        } else {
                            Log.d(TAG, "User rejected or unclear - staying on current phase")
                            if (currentTask.phase == TaskPhase.VALIDATION) {
                                // В VALIDATION: возврат на EXECUTION для исправлений
                                Log.d(TAG, "VALIDATION: Returning to EXECUTION for corrections")
                                transitionTaskTo(TaskPhase.EXECUTION)
                            } else {
                                // Другие фазы: просто сбрасываем подтверждение
                                resetAwaitingConfirmation()
                            }
                        }
                        return
                     }

                    // ПРИОРИТЕТ 1.5: Проверка на преждевременный step_completed при awaitingConfirmation
                    if (!currentTask.awaitingUserConfirmation && aiResponse.stepCompleted && aiResponse.transitionTo == null) {
                        // LLM пометил шаг как завершён, но не запрашивал подтверждение
                        // Это может быть ошибкой - логируем
                        Log.w(TAG, "⚠️ Potential issue: step_completed=true without awaitingConfirmation")
                        Log.w(TAG, "   Phase: ${currentTask.phase.label}, transitionTo: null")
                        Log.w(TAG, "   This should only happen in non-awaiting mode")
                    }

                    // ПРИОРИТЕТ 2: Явный переход через transitionTo
                    if (aiResponse.transitionTo != null) {
                        Log.d(TAG, "Explicit transition requested to: ${aiResponse.transitionTo.label}")

                        // Дополнительная проверка: запрещённые автоматические переходы
                        val isAutoTransition = when {
                            currentTask.phase == TaskPhase.EXECUTION && aiResponse.transitionTo == TaskPhase.VALIDATION -> true
                            currentTask.phase == TaskPhase.PLANNING && aiResponse.transitionTo == TaskPhase.VALIDATION -> true
                            else -> false
                        }

                        if (isAutoTransition) {
                            Log.w(TAG, "❌ Auto-transition detected: ${currentTask.phase.label} → ${aiResponse.transitionTo.label}")

                            val parentMessageId = if (_messages.value.isNotEmpty()) _messages.value.last().id else null
                            val warningMessage = ChatMessage(
                                id = (System.currentTimeMillis() + 1).toString(),
                                parentMessageId = parentMessageId,
                                content = """
                                    ⚠️ Автоматический переход на ${aiResponse.transitionTo.label} запрещен!

                                    💡 Правильный протокол для завершения фазы ${currentTask.phase.label}:
                                    1. Представьте результат работы
                                    2. Задайте вопрос пользователю
                                    3. Установите step_completed: true
                                    4. Система автоматически перейдет на следующую фазу после подтверждения

                                    ❌ Что запрещено:
                                    • Использовать transition_to для автоматических переходов вперед
                                    • Использовать фразы "Переходим к проверке", "Приступаем к проверке"

                                    ✅ Правильный пример:
                                    Вот план тренировок на неделю:
                                    Пн: Грудь + Трицепс
                                    ...

                                    Всё устраивает?

                                    step_completed: true
                                    transition_to: null
                                """.trimIndent(),
                                isFromUser = false,
                                isSystemMessage = true,
                                branchId = activeBranchId
                            )
                            _messages.value += warningMessage
                            return
                        }

                        val stateMachine = TaskStateMachine()
                        val validationResult = stateMachine.validateTransitionBefore(
                            currentTask.phase,
                            aiResponse.transitionTo
                        )

                        when (validationResult) {
                            is TransitionResult.Allowed -> {
                                Log.d(TAG, "✅ Transition ALLOWED by validation")
                                transitionTaskTo(aiResponse.transitionTo)
                            }
                            is TransitionResult.Denied -> {
                                Log.w(TAG, "❌ Transition DENIED by validation: ${validationResult.reason}")

                                val parentMessageId = if (_messages.value.isNotEmpty()) _messages.value.last().id else null
                                val warningMessage = ChatMessage(
                                    id = (System.currentTimeMillis() + 1).toString(),
                                    parentMessageId = parentMessageId,
                                    content = """
                                        ⚠️ ${validationResult.reason}

                                        💡 ${getValidTransitionsHint(currentTask.phase)}
                                    """.trimIndent(),
                                    isFromUser = false,
                                    isSystemMessage = true,
                                    branchId = activeBranchId
                                )
                                _messages.value += warningMessage
                            }
                        }
                        return
                    }

                    // ПРИОРИТЕТ 3: PLANNING - проверка готовности плана
                    if (!currentTask.awaitingUserConfirmation && currentTask.phase == TaskPhase.PLANNING) {
                        if (aiResponse.planReady) {
                            Log.d(TAG, "✅ PLANNING: plan_ready=true detected - awaiting confirmation")
                            setAwaitingConfirmation(true)
                            return
                        }

                        // Warning если LLM выдает детальный контент без plan_ready
                        if (containsDetailedExerciseContent(aiResponse.result)) {
                            Log.w(TAG, "⚠️ PLANNING: LLM returned detailed content without plan_ready marker")
                            Log.w(TAG, "   Response preview: ${aiResponse.result.take(200)}")
                            Log.w(TAG, "   This may indicate PLANNING rules violation")
                        }
                    }



                    // ПРИОРИТЕТ 4: Задача завершена (из VALIDATION)
                    if (aiResponse.taskCompleted) {
                        Log.d(TAG, "Task completed: true")

                        if (currentTask.phase == TaskPhase.VALIDATION) {
                            Log.d(TAG, "✅ Task completion ALLOWED: from VALIDATION phase")
                            transitionTaskTo(TaskPhase.DONE)
                        } else {
                            Log.w(TAG, "❌ Task completion DENIED: current phase is ${currentTask.phase.label}")
                            Log.w(TAG, "   Reason: Task completion is only allowed from VALIDATION phase")

                            val parentMessageId = if (_messages.value.isNotEmpty()) _messages.value.last().id else null
                            val warningMessage = ChatMessage(
                                id = (System.currentTimeMillis() + 1).toString(),
                                parentMessageId = parentMessageId,
                                content = """
                                    ⚠️ Нельзя завершить задачу из фазы "${currentTask.phase.label}"

                                    💡 Завершение задачи разрешено только после фазы проверки (VALIDATION)
                                    💡 ${getValidTransitionsHint(currentTask.phase)}
                                """.trimIndent(),
                                isFromUser = false,
                                isSystemMessage = true,
                                branchId = activeBranchId
                            )
                            _messages.value += warningMessage
                        }
                        return
                    }

                    // ПРИОРИТЕТ 5: Шаг завершён
                    if (aiResponse.stepCompleted) {
                        Log.d(TAG, "Step completed: true")
                        if (currentTask.currentStep >= currentTask.totalSteps) {
                            // EXECUTION: автоматический переход в VALIDATION без подтверждения
                            if (currentTask.phase == TaskPhase.EXECUTION) {
                                Log.d(TAG, "EXECUTION: Last step completed, auto-transitioning to VALIDATION")
                                transitionTaskTo(TaskPhase.VALIDATION)
                            } else {
                                // Другие фазы: требуем подтверждение пользователя
                                Log.d(TAG, "Last step completed, awaiting user confirmation")
                                setAwaitingConfirmation(true)
                            }
                        } else {
                            Log.d(TAG, "Advancing to next step: ${currentTask.currentStep + 1}/${currentTask.totalSteps}")
                            advanceTask()
                        }
                    } else {
                        Log.d(TAG, "No transition action taken")
                    }
                } else {
                    Log.d(TAG, "Has active task for intent processing: false")

                    // ========== НОВАЯ ПРОВЕРКА: Защита от дублирования ==========
                    if (currentTask != null) {
                        Log.w(TAG, "LLM returned CONTINUE/CLARIFICATION but task already exists")
                        Log.w(TAG, "  Task ID: ${currentTask.taskId}")
                        Log.w(TAG, "  Phase: ${currentTask.phase.label}")
                        Log.w(TAG, "  Skipping fallback task creation")
                        // Не создаём дубликат задачи
                    } else {
                        Log.w(TAG, "No active task but LLM returned CONTINUE/CLARIFICATION. Creating new task.")
                        val taskQuery = aiResponse.newTaskQuery ?: userInput
                        createTask(taskQuery)
                    }
                    // ================================================================
                }
            }
        }
        Log.d(TAG, "=== Task Intent Handler End ===\n")
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
                messages = emptyList(),
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

            val isBranching =
                type == ContextStrategyType.BRANCHING
            _chatUiState.value = _chatUiState.value.copy(
                isBranchingStrategy = isBranching,
                messages = emptyList(),
                activeBranchId = null,
                activeBranchName = null,
                currentBranchCheckpointMessageId = null,
                availableBranches = emptyList()
            )

            factRepository.clearAllFacts()
            branchRepository.clearAllBranches()
            chatRepository.deleteMessagesByBranch("main")

            _messages.value = emptyList()

            if (isBranching) {
                val strategy = contextStrategyFactory.create(chatSettingsRepository.getSettings())
                if (strategy is BranchingStrategy) {
                    strategy.initialize()
                }
            }
        }
    }

    fun createTask(query: String) {
        Log.d(TAG, "=== Creating Task ===")
        Log.d(TAG, "Query: $query")
        Log.d(TAG, "Profile: ${_chatUiState.value.fitnessProfile}")
        viewModelScope.launch {
            val profile = _chatUiState.value.fitnessProfile
            val task = taskRepository.createTask(query, profile)
            _taskContext.value = task
            Log.d(TAG, "Task created:")
            Log.d(TAG, "  ID: ${task.taskId}")
            Log.d(TAG, "  Phase: ${task.phase.label} (${task.currentStep}/${task.totalSteps})")
            Log.d(TAG, "  isActive: ${task.isActive}")
            Log.d(TAG, "=== Task Created ===\n")
        }
    }

    fun advanceTask() {
        viewModelScope.launch {
            val currentTask = _taskContext.value ?: return@launch
            Log.d(TAG, "=== Advancing Task ===")
            Log.d(TAG, "Task ID: ${currentTask.taskId}")
            Log.d(TAG, "Before: ${currentTask.phase.label} (step ${currentTask.currentStep}/${currentTask.totalSteps})")
            if (currentTask.canAdvance) {
                val updatedTask = taskRepository.updateTask(
                    currentTask.taskId,
                    TaskAction.AdvanceStep()
                )
                _taskContext.value = updatedTask
                updatedTask?.let {
                    Log.d(TAG, "After: ${it.phase.label} (step ${it.currentStep}/${it.totalSteps})")
                }
                Log.d(TAG, "=== Task Advanced ===\n")
            } else {
                Log.d(TAG, "Task cannot advance: isCompleted=${currentTask.isCompleted}, currentStep=${currentTask.currentStep}, totalSteps=${currentTask.totalSteps}")
                Log.d(TAG, "=== Task Advance Skipped ===\n")
            }
        }
    }

    fun completeTask(finalResult: String = "") {
        viewModelScope.launch {
            val currentTask = _taskContext.value ?: return@launch
            Log.d(TAG, "=== Completing Task ===")
            Log.d(TAG, "Task ID: ${currentTask.taskId}")
            Log.d(TAG, "Query: ${currentTask.query}")
            Log.d(TAG, "From: ${currentTask.phase.label}")
            Log.d(TAG, "Final result: ${finalResult.ifEmpty { "Not provided" }}")
            val updatedTask = taskRepository.updateTask(
                currentTask.taskId,
                TaskAction.Complete(finalResult)
            )
            _taskContext.value = updatedTask
            updatedTask?.let {
                Log.d(TAG, "After: ${it.phase.label}")
            }
            Log.d(TAG, "=== Task Completed ===\n")
        }
    }

    fun pauseTask() {
        viewModelScope.launch {
            val currentTask = _taskContext.value ?: return@launch
            Log.d(TAG, "=== Pausing Task ===")
            Log.d(TAG, "Task ID: ${currentTask.taskId}")
            Log.d(TAG, "Query: ${currentTask.query}")
            Log.d(TAG, "Current phase: ${currentTask.phase.label} (step ${currentTask.currentStep}/${currentTask.totalSteps})")
            val updatedTask = taskRepository.updateTask(
                currentTask.taskId,
                TaskAction.Pause(currentTask.taskId)
            )
            _taskContext.value = updatedTask
            updatedTask?.let {
                Log.d(TAG, "After: isActive=${it.isActive}")
            }
            Log.d(TAG, "=== Task Paused ===\n")
        }
    }

    fun resumeTask() {
        viewModelScope.launch {
            val currentTask = _taskContext.value ?: return@launch
            Log.d(TAG, "=== Resuming Task ===")
            Log.d(TAG, "Task ID: ${currentTask.taskId}")
            Log.d(TAG, "Query: ${currentTask.query}")
            Log.d(TAG, "Current phase: ${currentTask.phase.label} (step ${currentTask.currentStep}/${currentTask.totalSteps})")
            val updatedTask = taskRepository.updateTask(
                currentTask.taskId,
                TaskAction.Resume
            )
            _taskContext.value = updatedTask
            updatedTask?.let {
                Log.d(TAG, "After: isActive=${it.isActive}")
            }
            Log.d(TAG, "=== Task Resumed ===\n")
        }
    }

    fun transitionTaskTo(toPhase: TaskPhase) {
        viewModelScope.launch {
            val currentTask = _taskContext.value ?: return@launch
            Log.d(TAG, "=== Transitioning Task ===")
            Log.d(TAG, "Task ID: ${currentTask.taskId}")
            Log.d(TAG, "From: ${currentTask.phase.label} (step ${currentTask.currentStep}/${currentTask.totalSteps})")
            Log.d(TAG, "To: ${toPhase.label}")

            val stateMachine = TaskStateMachine()
            val canTransition = stateMachine.canTransition(currentTask.phase, toPhase)
            Log.d(TAG, "Can transition: $canTransition")

            if (canTransition) {
                val updatedTask = taskRepository.updateTask(
                    currentTask.taskId,
                    TaskAction.Transition(toPhase)
                )
                _taskContext.value = updatedTask
                updatedTask?.let {
                    Log.d(TAG, "After: ${it.phase.label} (step ${it.currentStep}/${it.totalSteps})")
                }
                Log.d(TAG, "=== Task Transitioned ===\n")
            } else {
                Log.w(TAG, "Transition not allowed! Valid transitions: ${stateMachine.getPossibleTransitions(currentTask.phase).map { it.label }}")
                Log.d(TAG, "=== Task Transition Skipped ===\n")
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

            val fullPath = chatRepository.getBranchPathWithCheckpoint(branchId)
            _messages.value = fullPath

            val activeBranch = branchRepository.getBranchById(branchId).first()
            _chatUiState.value = _chatUiState.value.copy(
                showBranchPicker = false,
                messages = fullPath,
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

            val newBranch = ChatBranch(
                id = newBranchId,
                parentBranchId = activeBranchId,
                checkpointMessageId = targetMessageId,
                lastMessageId = targetMessageId,
                title = state.newBranchName,
                createdAt = System.currentTimeMillis()
            )

            branchRepository.createBranch(newBranch)

            if (switchToNew) {
                branchRepository.setActiveBranchId(newBranchId)

                val fullPath = chatRepository.getBranchPathWithCheckpoint(newBranchId)
                _messages.value = fullPath

                _chatUiState.value = _chatUiState.value.copy(
                    showCreateBranchDialog = false,
                    activeBranchId = newBranchId,
                    activeBranchName = state.newBranchName,
                    messages = fullPath,
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
                val messages = chatRepository.getBranchPathWithCheckpoint(mainBranch)
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

    fun setFitnessProfile(profile: FitnessProfileType) {
        viewModelScope.launch {
            fitnessProfileManager.setActiveProfile(profile)
            _chatUiState.value = _chatUiState.value.copy(
                fitnessProfile = profile
            )
        }
    }

    private fun parseAffirmativeResponse(userInput: String): Boolean {
        val affirmativeKeywords = listOf(
            "да", "утверждаю", "хорошо", "согласен", "yes", "ок", "окей",
            "давай", "отлично", "супер", "конечно", "разумеется", "несомненно",
            "ладно", "норм", "нормально", "го", "погнали", "поехали",
            "ясно", "принято", "записано", "принимаю", "выглядит неплохо",
            "выглядит хорошо", "вроде бы", "думаю да", "наверное да", "пожалуй",
            "ну ладно", "ну ок", "ну давай", "окей давай", "ого круто", "класс"
        )
        val responseText = userInput.lowercase()
        return affirmativeKeywords.any { it in responseText }
    }

    private fun parseDisagreementResponse(aiResponse: EnhancedTaskAiResponse): Boolean {
        val disagreementKeywords = listOf(
            "нет", "не нравится", "не устраивает", "хочу изменить",
            "переделай", "исправь", "это неправильно", "плохо",
            "неудачно", "не то", "другой вариант", "изменить", "измени",
            "переделать", "переделай", "не работает", "сложно", "слишком сложно"
        )
        val responseText = aiResponse.result.lowercase()
        return disagreementKeywords.any { it in responseText }
    }

    private fun setAwaitingConfirmation(awaiting: Boolean) {
        viewModelScope.launch {
            val currentTask = _taskContext.value ?: return@launch
            Log.d(TAG, "=== Setting AwaitingConfirmation ===")
            Log.d(TAG, "Task ID: ${currentTask.taskId}")
            Log.d(TAG, "From: ${currentTask.awaitingUserConfirmation}")
            Log.d(TAG, "To: $awaiting")
            val updatedTask = taskRepository.updateTask(
                currentTask.taskId,
                TaskAction.SetAwaitingConfirmation(awaiting)
            )
            _taskContext.value = updatedTask
            updatedTask?.let {
                Log.d(TAG, "After: ${it.awaitingUserConfirmation}")
            }
            Log.d(TAG, "=== AwaitingConfirmation Set ===\n")
        }
    }

    private fun resetAwaitingConfirmation() {
        setAwaitingConfirmation(false)
    }

    private fun containsDetailedExerciseContent(response: String): Boolean {
        val exercisePatterns = listOf(
            Regex("""\d+\s*подход.*\d+\s*повтор""", RegexOption.IGNORE_CASE),
            Regex("""жим\s+\w+.*\d+""", RegexOption.IGNORE_CASE),
            Regex("""присед.*\d+""", RegexOption.IGNORE_CASE),
            Regex("""тяга.*\d+""", RegexOption.IGNORE_CASE),
            Regex("""\d+\s*x\s*\d+""", RegexOption.IGNORE_CASE),  // 5x5 формат
            Regex("""день\s+\d+:""", RegexOption.IGNORE_CASE)  // "День 1:"
        )
        return exercisePatterns.any { it.find(response) != null }
    }

    private fun getValidTransitionsHint(phase: TaskPhase): String {
        val transitions = TaskStateMachine().getPossibleTransitions(phase)
        return if (transitions.isEmpty()) {
            "Нет допустимых переходов (финальная фаза)"
        } else {
            "Допустимые переходы: ${transitions.joinToString { it.label }}"
        }
    }
}
