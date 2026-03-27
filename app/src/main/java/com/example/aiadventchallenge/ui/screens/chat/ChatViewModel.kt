package com.example.aiadventchallenge.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.config.CompressionConfig
import com.example.aiadventchallenge.data.mapper.MessageMapper
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.AnswerWithUsage
import com.example.aiadventchallenge.domain.model.ApiMessageDebug
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.CompressedChatHistory
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.RequestConfigDebug
import com.example.aiadventchallenge.domain.model.RequestLog
import com.example.aiadventchallenge.domain.model.SummaryMessage
import com.example.aiadventchallenge.domain.usecase.CreateSummaryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(
    private val agent: ChatAgent,
    private val chatRepository: ChatRepository,
    private val createSummaryUseCase: CreateSummaryUseCase
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastRequestTokens = MutableStateFlow<LastRequestTokens?>(null)
    val lastRequestTokens: StateFlow<LastRequestTokens?> = _lastRequestTokens.asStateFlow()

    private val _dialogStats = MutableStateFlow(DialogTokenStats())
    val dialogStats: StateFlow<DialogTokenStats> = _dialogStats.asStateFlow()

    private val _requestLogs = MutableStateFlow<List<RequestLog>>(emptyList())
    val requestLogs: StateFlow<List<RequestLog>> = _requestLogs.asStateFlow()

    data class LastRequestTokens(
        val promptTokens: Int?,
        val completionTokens: Int?,
        val totalTokens: Int?
    )

    init {
        loadMessagesFromDatabase()
        loadDialogStats()
    }

    private fun loadMessagesFromDatabase() {
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { messages ->
                _messages.value = messages
            }
        }
    }

    private fun loadDialogStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _dialogStats.value = chatRepository.getDialogStats()
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

        val userMessage = ChatMessage(
            id = System.currentTimeMillis().toString(),
            content = userInput,
            isFromUser = true
        )
        _messages.value += userMessage

        viewModelScope.launch {
            chatRepository.insertMessage(userMessage)

            val config = agent.buildRequestConfig()

            val compressedHistory = getCompressedHistory()
            val apiMessages = MessageMapper.mapCompressedToApiMessages(compressedHistory, config.systemPrompt)

            println("📤 API request:")
            println("  Summaries: ${compressedHistory.summaries.size}")
            println("  Recent messages: ${compressedHistory.recentMessages.size}")
            println("  Total messages: ${apiMessages.size - 1} (excluding system prompt)")

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

            when (val result = agent.processRequestWithCompressedContextAndUsage(compressedHistory, config)) {
                is ChatResult.Success -> {
                    val answerWithUsage = result.data

                    _lastRequestTokens.value = LastRequestTokens(
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    )

                    addRequestLog(preliminaryLog.copy(
                        responseContent = answerWithUsage.content,
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    ))

                    val aiMessage = ChatMessage(
                        id = (System.currentTimeMillis() + 1).toString(),
                        content = answerWithUsage.content,
                        isFromUser = false,
                        promptTokens = answerWithUsage.promptTokens,
                        completionTokens = answerWithUsage.completionTokens,
                        totalTokens = answerWithUsage.totalTokens
                    )
                    _messages.value += aiMessage
                    chatRepository.insertMessage(aiMessage)

                    if (chatRepository.shouldCreateSummary()) {
                        createSummaryIfNeeded(_messages.value)
                    }

                    loadDialogStats()
                }
                is ChatResult.Error -> {
                    addRequestLog(preliminaryLog.copy(
                        responseError = result.message,
                        promptTokens = null,
                        completionTokens = null,
                        totalTokens = null
                    ))

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

    private suspend fun createSummaryIfNeeded(messages: List<ChatMessage>) {
        val messagesToSummarize = messages.takeLast(CompressionConfig.SUMMARY_INTERVAL)

        val firstMessage = messagesToSummarize.firstOrNull()
        val lastMessage = messagesToSummarize.lastOrNull()

        println("📝 Summary:")
        println("  Messages: ${messagesToSummarize.size}")
        if (firstMessage != null) {
            val role = if (firstMessage.isFromUser) "User" else "AI"
            println("  First ($role): ${firstMessage.content}")
        }
        if (lastMessage != null && lastMessage != firstMessage) {
            val role = if (lastMessage.isFromUser) "User" else "AI"
            println("  Last ($role): ${lastMessage.content}")
        }

        when (val result = createSummaryUseCase(messagesToSummarize)) {
            is ChatResult.Success -> {
                val answerWithUsage = result.data

                println("  ✓ Summary: ${answerWithUsage.content}")
                println("  Tokens: ${answerWithUsage.totalTokens}")

                val firstMessageId = messagesToSummarize.first().id.toLong()
                val lastMessageId = messagesToSummarize.last().id.toLong()

                val summary = SummaryMessage(
                    id = "summary_${System.currentTimeMillis()}",
                    content = answerWithUsage.content,
                    messageRangeStart = firstMessageId,
                    messageRangeEnd = lastMessageId,
                    messageCount = messagesToSummarize.size
                )

                chatRepository.insertSummary(summary)

                val allSummaries = chatRepository.getAllSummaries()
                if (allSummaries.size > 5) {
                    val summaryToDelete = allSummaries.first()
                    println("  ✗ Delete old: ${summaryToDelete.id}")
                    chatRepository.deleteSummaryByRangeEnd(summaryToDelete.messageRangeEnd)
                }

                addRequestLog(RequestLog(
                    id = "summary_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    requestConfig = RequestConfigDebug(
                        model = null,
                        temperature = null,
                        maxTokens = null,
                        systemPrompt = "Summary creation"
                    ),
                    requestMessages = emptyList(),
                    responseContent = answerWithUsage.content,
                    responseError = null,
                    promptTokens = answerWithUsage.promptTokens,
                    completionTokens = answerWithUsage.completionTokens,
                    totalTokens = answerWithUsage.totalTokens
                ))
            }
            is ChatResult.Error -> {
                println("  ✗ Error: ${result.message}")

                addRequestLog(RequestLog(
                    id = "summary_${System.currentTimeMillis()}",
                    timestamp = System.currentTimeMillis(),
                    requestConfig = RequestConfigDebug(
                        model = null,
                        temperature = null,
                        maxTokens = null,
                        systemPrompt = "Summary creation"
                    ),
                    requestMessages = emptyList(),
                    responseContent = null,
                    responseError = result.message,
                    promptTokens = null,
                    completionTokens = null,
                    totalTokens = null
                ))
            }
        }
    }

    private suspend fun getCompressedHistory(): CompressedChatHistory {
        return chatRepository.getCompressedHistory()
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.deleteAllMessages()
            _dialogStats.value = DialogTokenStats()
            _lastRequestTokens.value = null
            _requestLogs.value = emptyList()
        }
    }
}
