package com.example.aiadventchallenge.domain.chat

import android.util.Log
import java.util.UUID
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.data.config.TaskPromptBuilder
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChatMessageHandlerImpl(
    private val chatRepository: ChatRepository,
    private val agent: ChatAgent,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val chatSettingsRepository: ChatSettingsRepository
) : ChatMessageHandler {

    private val TAG = "ChatMessageHandler"

    private val classificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    
    override suspend fun handleUserMessage(
        userInput: String,
        taskContext: TaskContext?,
        fitnessProfile: FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String?
    ): ChatMessageResult {
        logLlmRequest(userInput, taskContext)
        
        val userMessage = createUserMessage(
            userInput = userInput,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        
        chatRepository.insertMessage(userMessage, activeBranchId, parentMessageId)
        
        val activeMessages = chatRepository.getMessagesByBranch(activeBranchId)
        
        var config = agent.buildRequestConfigWithTask(
            taskContext = taskContext,
            fitnessProfile = fitnessProfile,
            userInput = userInput
        )
        
        if (mcpContext != null) {
            config = config.copy(
                systemPrompt = config.systemPrompt + mcpContext
            )
        }
        
        val strategy = contextStrategyFactory.create(chatSettingsRepository.getSettings())
        val apiMessages = strategy.buildContext(null, activeMessages, config.systemPrompt)
        
        return when (val result = agent.processRequestWithContextAndUsage(
            messages = apiMessages,
            config = config,
            userInput = userInput,
            taskContext = taskContext
        )) {
            is ChatResult.Success -> {
                val answerWithUsage = result.data
                val aiResponse = TaskPromptBuilder.parseEnhancedAiResponse(answerWithUsage.content)
                
                logLlmResponse(aiResponse, answerWithUsage.totalTokens ?: 0, answerWithUsage.content.length)
                
                if (aiResponse.result.isEmpty()) {
                    return handleEmptyResponse(userMessage, activeBranchId)
                }
                
                val aiMessage = createAiMessage(
                    content = aiResponse.result,
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
                    aiResponse = aiResponse
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
        parentMessageId: String,
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
        userInput: String,
        taskContext: TaskContext?
    ) {
        Log.d(TAG, "📤 === OUTGOING LLM REQUEST ===")
        Log.d(TAG, "   User input: $userInput")
        Log.d(TAG, "   Phase: ${taskContext?.phase?.label}")
        Log.d(TAG, "   Active task: ${taskContext?.query}")
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
        
        if (aiResponse.result.isEmpty()) {
            Log.w(TAG, "⚠️ Empty result detected - will show error to user")
        } else {
            Log.d(TAG, "   Response preview: ${aiResponse.result.take(150)}...")
        }
        
        Log.d(TAG, "   ===============================")
    }
}
