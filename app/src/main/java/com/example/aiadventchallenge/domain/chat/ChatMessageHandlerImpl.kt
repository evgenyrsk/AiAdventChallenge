package com.example.aiadventchallenge.domain.chat

import android.util.Log
import java.util.UUID
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mapper.MessageMapper
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType

import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.model.AnswerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.aiadventchallenge.domain.usecase.PrepareRagRequestUseCase
import com.example.aiadventchallenge.data.model.Message

class ChatMessageHandlerImpl(
    private val chatRepository: ChatRepository,
    private val agent: ChatAgent,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val prepareRagRequestUseCase: PrepareRagRequestUseCase
) : ChatMessageHandler {

    private val TAG = "ChatMessageHandler"

    private val classificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    
    override suspend fun handleUserMessage(
        userInput: String,
        fitnessProfile: FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String?,
        answerMode: AnswerMode
    ): ChatMessageResult {
        logLlmRequest(userInput)
        
        val userMessage = createUserMessage(
            userInput = userInput,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        chatRepository.insertMessage(userMessage, activeBranchId, parentMessageId)
        
        val activeMessages = chatRepository.getMessagesByBranch(activeBranchId)
        
        var config = agent.buildRequestConfigWithProfile(
            fitnessProfile = fitnessProfile
        )
        
        if (mcpContext != null) {
            Log.d(TAG, "🔧 Adding MCP context to system prompt (length=${mcpContext.length})")
            Log.d(TAG, "🔧 MCP Context preview: ${mcpContext.take(200)}...")
            config = config.copy(
                systemPrompt = config.systemPrompt + mcpContext
            )
        } else {
            Log.d(TAG, "ℹ️ No MCP context to add")
        }
        
        val strategy = contextStrategyFactory.create(chatSettingsRepository.getSettings())
        val apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)

        return when (val result = agent.processRequestWithContextAndUsage(
            messages = apiMessages,
            config = config,
            userInput = userInput,
            taskContext = null
        )) {
            is ChatResult.Success -> {
                val answerWithUsage = result.data
                val aiResponseText = answerWithUsage.content
                
                logLlmResponse(aiResponseText, answerWithUsage.totalTokens ?: 0, aiResponseText.length)
                
                if (aiResponseText.isEmpty()) {
                    return handleEmptyResponse(userMessage, activeBranchId)
                }
                
                val aiMessage = createAiMessage(
                    content = aiResponseText,
                    parentMessageId = userMessage.id,
                    activeBranchId = activeBranchId,
                    promptTokens = answerWithUsage.promptTokens,
                    completionTokens = answerWithUsage.completionTokens,
                    totalTokens = answerWithUsage.totalTokens
                )
                
                chatRepository.insertMessage(aiMessage, activeBranchId, aiMessage.parentMessageId)

                classificationScope.launch {
                    try {
                        strategy.onConversationPair(userMessage, aiMessage)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in background classification: ${e.message}", e)
                    }
                }

                ChatMessageResult.Success(
                    userMessage = userMessage,
                    aiMessage = aiMessage,
                    aiResponse = aiResponseText
                )
            }
            is ChatResult.Error -> {
                handleErrorResponse(
                    userMessage = userMessage,
                    activeBranchId = activeBranchId,
                    errorMessage = result.message
                )
            }
        }
    }
    
    override suspend fun saveUserMessage(
        userInput: String,
        activeBranchId: String,
        parentMessageId: String?
    ): ChatMessage {
        val userMessage = createUserMessage(
            userInput = userInput,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        chatRepository.insertMessage(userMessage, activeBranchId, parentMessageId)
        return userMessage
    }
    
    override suspend fun generateAiResponse(
        userInput: String,
        fitnessProfile: FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String?,
        answerMode: AnswerMode
    ): ChatMessageResult {
        val activeMessages = chatRepository.getMessagesByBranch(activeBranchId)

        var config = agent.buildRequestConfigWithProfile(fitnessProfile)

        if (mcpContext != null) {
            Log.d(TAG, "🔧 Adding MCP context to system prompt (length=${mcpContext.length})")
            config = config.copy(systemPrompt = config.systemPrompt + mcpContext)
        } else {
            Log.d(TAG, "ℹ️ No MCP context to add")
        }

        val strategy = contextStrategyFactory.create(chatSettingsRepository.getSettings())
        var apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)
        var retrievalSummary: com.example.aiadventchallenge.domain.mcp.RetrievalSummary? = null

        if (answerMode == AnswerMode.RAG_BASIC || answerMode == AnswerMode.RAG_ENHANCED) {
            val ragConfig = when (answerMode) {
                AnswerMode.RAG_ENHANCED -> FitnessRagConfig.enhancedPipeline
                AnswerMode.RAG_BASIC -> FitnessRagConfig.basicPipeline
                AnswerMode.PLAIN_LLM -> null
            }
            runCatching {
                prepareRagRequestUseCase(
                    question = userInput,
                    config = requireNotNull(ragConfig)
                )
            }.onSuccess { preparedRagRequest ->
                retrievalSummary = preparedRagRequest.retrievalSummary
                config = config.copy(
                    systemPrompt = config.systemPrompt + "\n\n" + preparedRagRequest.systemPromptSuffix
                )
                apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)
                apiMessages = replaceLastUserMessage(apiMessages, preparedRagRequest.userPrompt)
            }.onFailure { error ->
                Log.e(TAG, "❌ RAG retrieval failed, falling back to base prompt", error)
                config = config.copy(
                    systemPrompt = config.systemPrompt + "\n\nRAG MODE\nЕсли контекста недостаточно или retrieval недоступен, прямо скажи об этом и не выдумывай факты."
                )
                apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)
            }
        }

        return when (val result = agent.processRequestWithContextAndUsage(
            messages = apiMessages,
            config = config,
            userInput = userInput,
            taskContext = null
        )) {
            is ChatResult.Success -> {
                val answerWithUsage = result.data
                val aiResponseText = answerWithUsage.content.trim()
                
                logLlmResponse(aiResponseText, answerWithUsage.totalTokens ?: 0, aiResponseText.length)
                
                if (aiResponseText.isEmpty()) {
                    return ChatMessageResult.EmptyResponse("Пустой ответ от AI", null)
                }
                
                val aiMessage = createAiMessage(
                    content = aiResponseText,
                    parentMessageId = parentMessageId,
                    activeBranchId = activeBranchId,
                    promptTokens = answerWithUsage.promptTokens,
                    completionTokens = answerWithUsage.completionTokens,
                    totalTokens = answerWithUsage.totalTokens
                )
                
                chatRepository.insertMessage(aiMessage, activeBranchId, aiMessage.parentMessageId)
                
                ChatMessageResult.Success(
                    userMessage = null,
                    aiMessage = aiMessage,
                    aiResponse = aiResponseText,
                    retrievalSummary = retrievalSummary
                )
            }
            is ChatResult.Error -> {
                Log.e(TAG, "❌ AI response error: ${result.message}")
                ChatMessageResult.Error(result.message, null)
            }
        }
    }

    private fun replaceLastUserMessage(
        messages: List<Message>,
        augmentedPrompt: String
    ): List<Message> {
        val lastUserIndex = messages.indexOfLast { it.role == com.example.aiadventchallenge.data.model.MessageRole.USER.value }
        if (lastUserIndex == -1) return messages

        return messages.toMutableList().apply {
            this[lastUserIndex] = Message(
                role = com.example.aiadventchallenge.data.model.MessageRole.USER,
                content = augmentedPrompt
            )
        }
    }
    
    override suspend fun handleSystemPrompt(systemPrompt: String): SystemPromptResult {
        Log.d(TAG, "📤 === SYSTEM PROMPT REQUEST ===")
        Log.d(TAG, "   Prompt: $systemPrompt")
        
        val config = RequestConfig(
            systemPrompt = systemPrompt,
            temperature = 0.7
        )
        
        val messages = listOf(
            com.example.aiadventchallenge.data.model.Message(
                role = com.example.aiadventchallenge.data.model.MessageRole.SYSTEM,
                content = systemPrompt
            ),
            com.example.aiadventchallenge.data.model.Message(
                role = com.example.aiadventchallenge.data.model.MessageRole.USER,
                content = "Пожалуйста, ответьте на запрос выше."
            )
        )
        
        return when (val result = agent.processRequestWithContextAndUsage(
            messages = messages,
            config = config,
            userInput = "Пожалуйста, ответьте на запрос выше.",
            taskContext = null
        )) {
            is ChatResult.Success -> {
                val answer = result.data
                val messageText = answer.content.trim()
                
                Log.d(TAG, "📥 === SYSTEM PROMPT RESPONSE ===")
                Log.d(TAG, "   Response: ${messageText.take(150)}...")
                
                if (messageText.isEmpty()) {
                    SystemPromptResult.Error("Получен пустой ответ от AI")
                } else {
                    SystemPromptResult.Success(messageText)
                }
            }
            is ChatResult.Error -> {
                Log.e(TAG, "❌ System prompt error: ${result.message}")
                SystemPromptResult.Error(result.message)
            }
        }
    }
    
    private fun createUserMessage(
        userInput: String,
        activeBranchId: String,
        parentMessageId: String?
    ): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            parentMessageId = parentMessageId,
            content = userInput,
            isFromUser = true,
            branchId = activeBranchId
        )
    }
    
    private fun createAiMessage(
        content: String,
        parentMessageId: String?,
        activeBranchId: String,
        promptTokens: Int?,
        completionTokens: Int?,
        totalTokens: Int?
    ): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            parentMessageId = parentMessageId,
            content = content,
            isFromUser = false,
            branchId = activeBranchId,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens
        )
    }
    
    private fun createSystemMessage(
        content: String,
        parentMessageId: String,
        activeBranchId: String
    ): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            parentMessageId = parentMessageId,
            content = content,
            isFromUser = false,
            isSystemMessage = true,
            branchId = activeBranchId
        )
    }
    
    private suspend fun handleEmptyResponse(
        userMessage: ChatMessage,
        activeBranchId: String
    ): ChatMessageResult {
        Log.w(TAG, "Empty response detected - will show error to user")

        val errorMessageContent = "❌ Не удалось получить ответ от ассистента. Попробуйте переформулировать запрос."

        return ChatMessageResult.EmptyResponse(
            errorMessage = errorMessageContent,
            userMessage = userMessage
        )
    }
    
    private suspend fun handleErrorResponse(
        userMessage: ChatMessage,
        activeBranchId: String,
        errorMessage: String
    ): ChatMessageResult {
        Log.e(TAG, "LLM Error: $errorMessage")

        return ChatMessageResult.Error(
            errorMessage = errorMessage,
            userMessage = userMessage
        )
    }
    
    private fun logLlmRequest(
        userInput: String
    ) {
        Log.d(TAG, "📤 === OUTGOING LLM REQUEST ===")
        Log.d(TAG, "   User input: $userInput")
        Log.d(TAG, "   ============================")
    }
    
    private fun logLlmResponse(
        aiResponse: String,
        totalTokens: Int,
        contentLength: Int
    ) {
        Log.d(TAG, "📥 === INCOMING LLM RESPONSE ===")
        Log.d(TAG, "   Raw content length: $contentLength")
        Log.d(TAG, "   Tokens: $totalTokens")
        
        if (aiResponse.isEmpty()) {
            Log.w(TAG, "⚠️ Empty result detected - will show error to user")
        } else {
            Log.d(TAG, "   Response preview: ${aiResponse.take(150)}...")
        }
        
        Log.d(TAG, "   ===============================")
    }
}
