package com.example.aiadventchallenge.domain.chat

import android.util.Log
import java.util.UUID
import com.example.aiadventchallenge.data.agent.ChatAgent
import com.example.aiadventchallenge.data.mapper.MessageMapper
import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.GroundedAnswerPayload
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.model.RequestConfig
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.context.ContextStrategyFactory
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.AiBackendSettings
import com.example.aiadventchallenge.domain.model.ChatAnswerPresentation
import com.example.aiadventchallenge.domain.model.ChatExecutionInfo
import com.example.aiadventchallenge.domain.model.ChatFailureCategory
import com.example.aiadventchallenge.domain.model.ChatSourcePreview
import com.example.aiadventchallenge.domain.model.RagAnswerMode
import com.example.aiadventchallenge.domain.model.AiBackendType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.aiadventchallenge.domain.usecase.PrepareRagRequestUseCase
import com.example.aiadventchallenge.data.model.Message
import kotlin.system.measureTimeMillis

class ChatMessageHandlerImpl(
    private val chatRepository: ChatRepository,
    private val agent: ChatAgent,
    private val contextStrategyFactory: ContextStrategyFactory,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val prepareRagRequestUseCase: PrepareRagRequestUseCase,
    private val localLlmProfileResolver: LocalLlmProfileResolver
) : ChatMessageHandler {

    private val TAG = "ChatMessageHandler"

    private val classificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val numberedSourcesBlockRegex = Regex(
        """\n\nИсточники:\n(?:\d+\.\s.+(?:\n|$))+${'$'}""",
        setOf(RegexOption.MULTILINE)
    )
    private val fallbackSourcesBlockRegex = Regex(
        """\n\nИсточники:\s*нет\s*[—-]\s*недостаточно релевантного контекста\.\s*${'$'}""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val englishSourcesBlockRegex = Regex(
        """\n\nSources:\n(?:[-*]\s.+(?:\n|$)|\d+\.\s.+(?:\n|$))+${'$'}""",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )
    private val englishInlineSourcesRegex = Regex(
        """\n\nSources:\s*[^\n]+(?:\n[^\n]+)*${'$'}""",
        setOf(RegexOption.IGNORE_CASE)
    )

    
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
        val backendSettings = chatSettingsRepository.getAiBackendSettings()
        val executionSettings = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = backendSettings.localConfig,
            answerMode = answerMode
        )
        var config = localLlmProfileResolver.applyToRequestConfig(
            baseConfig = agent.buildRequestConfigWithProfile(fitnessProfile = fitnessProfile),
            fitnessProfile = fitnessProfile,
            executionSettings = executionSettings
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
        val apiMessages = strategy.buildContext(
            null,
            sanitizeMessagesForLlmContext(activeMessages),
            config.systemPrompt
        )

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
        answerMode: AnswerMode,
        preparedRagRequest: PreparedRagRequest?
    ): ChatMessageResult {
        val backendSettings = chatSettingsRepository.getAiBackendSettings()
        val activeMessages = chatRepository.getMessagesByBranch(activeBranchId)
        val executionSettings = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = backendSettings.localConfig,
            answerMode = answerMode
        )
        var config = localLlmProfileResolver.applyToRequestConfig(
            baseConfig = agent.buildRequestConfigWithProfile(fitnessProfile),
            fitnessProfile = fitnessProfile,
            executionSettings = executionSettings
        )

        if (mcpContext != null) {
            Log.d(TAG, "🔧 Adding MCP context to system prompt (length=${mcpContext.length})")
            config = config.copy(systemPrompt = config.systemPrompt + mcpContext)
        } else {
            Log.d(TAG, "ℹ️ No MCP context to add")
        }

        val strategy = contextStrategyFactory.create(chatSettingsRepository.getSettings())
        val sanitizedMessages = sanitizeMessagesForLlmContext(activeMessages)
        var apiMessages = strategy.buildContext(null, sanitizedMessages, config.systemPrompt)
        var retrievalSummary: com.example.aiadventchallenge.domain.mcp.RetrievalSummary? = null
        var fallbackAnswerText: String? = null
        var retrievalLatencyMs: Long? = preparedRagRequest?.retrievalLatencyMs

        if (preparedRagRequest != null) {
            retrievalSummary = preparedRagRequest.retrievalSummary
            fallbackAnswerText = preparedRagRequest.fallbackAnswerText
            retrievalLatencyMs = preparedRagRequest.retrievalLatencyMs
            config = config.copy(
                systemPrompt = config.systemPrompt + "\n\n" + preparedRagRequest.systemPromptSuffix
            )
            apiMessages = buildRagEnhancedMessages(preparedRagRequest.userPrompt, config.systemPrompt)
        } else if (answerMode == AnswerMode.RAG_BASIC || answerMode == AnswerMode.RAG_ENHANCED) {
            val ragConfig = when (answerMode) {
                AnswerMode.RAG_ENHANCED -> FitnessRagConfig.enhancedPipeline
                AnswerMode.RAG_BASIC -> FitnessRagConfig.basicPipeline
                AnswerMode.PLAIN_LLM -> null
            }
            runCatching {
                prepareRagRequestUseCase(
                    question = userInput,
                    config = requireNotNull(ragConfig),
                    promptProfile = executionSettings.promptProfile
                )
            }.onSuccess { preparedRagRequest ->
                retrievalSummary = preparedRagRequest.retrievalSummary
                fallbackAnswerText = preparedRagRequest.fallbackAnswerText
                retrievalLatencyMs = preparedRagRequest.retrievalLatencyMs
                config = config.copy(
                    systemPrompt = config.systemPrompt + "\n\n" + preparedRagRequest.systemPromptSuffix
                )
                apiMessages = if (answerMode == AnswerMode.RAG_ENHANCED) {
                    buildRagEnhancedMessages(preparedRagRequest.userPrompt, config.systemPrompt)
                } else {
                    strategy.buildContext(null, sanitizedMessages, config.systemPrompt)
                        .let { replaceLastUserMessage(it, preparedRagRequest.userPrompt) }
                }
            }.onFailure { error ->
                Log.e(TAG, "❌ RAG retrieval failed, falling back to base prompt", error)
                config = config.copy(
                    systemPrompt = config.systemPrompt + "\n\nRAG MODE\nЕсли контекста недостаточно или retrieval недоступен, прямо скажи об этом и не выдумывай факты."
                )
                apiMessages = strategy.buildContext(null, sanitizedMessages, config.systemPrompt)
            }
        }

        if (!fallbackAnswerText.isNullOrBlank()) {
            val fallbackText = sanitizeAnswerTextForDisplay(fallbackAnswerText!!.trim())
            logLlmResponse(fallbackText, 0, fallbackText.length)

            val aiMessage = createAiMessage(
                content = fallbackText,
                parentMessageId = parentMessageId,
                activeBranchId = activeBranchId,
                promptTokens = 0,
                completionTokens = 0,
                totalTokens = 0
            )
            chatRepository.insertMessage(aiMessage, activeBranchId, aiMessage.parentMessageId)

            val updatedSummary = retrievalSummary?.let { summary ->
                val grounded = summary.groundedAnswer
                if (grounded != null) {
                    summary.copy(
                        groundedAnswer = grounded.copy(answerText = fallbackText)
                    )
                } else {
                    summary
                }
            }

            val executionInfo = buildExecutionInfo(
                backendSettings = backendSettings,
                answerMode = answerMode,
                latencyMs = 0L,
                retrievalLatencyMs = retrievalLatencyMs,
                generationLatencyMs = 0L,
                retrievalSummary = updatedSummary,
                messageId = aiMessage.id,
                requestConfig = config,
                responseChars = fallbackText.length,
                totalTokens = 0
            )
            return ChatMessageResult.Success(
                userMessage = null,
                aiMessage = aiMessage,
                aiResponse = fallbackText,
                retrievalSummary = updatedSummary,
                executionInfo = executionInfo,
                answerPresentation = buildAnswerPresentation(
                    aiMessage = aiMessage,
                    executionInfo = executionInfo,
                    retrievalSummary = updatedSummary
                )
            )
        }

        var llmResult: ChatResult<com.example.aiadventchallenge.domain.model.AnswerWithUsage>? = null
        val latencyMs = measureTimeMillis {
            llmResult = agent.processRequestWithContextAndUsage(
                messages = apiMessages,
                config = config,
                userInput = userInput,
                taskContext = null
            )
        }

        return when (val result = llmResult) {
            is ChatResult.Success -> {
                val answerWithUsage = result.data
                val aiResponseText = sanitizeAnswerTextForDisplay(answerWithUsage.content.trim())
                
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

                val updatedSummary = retrievalSummary?.let { summary ->
                    val grounded = summary.groundedAnswer
                    if (grounded != null) {
                        summary.copy(
                            groundedAnswer = grounded.copy(
                                answerText = aiResponseText,
                                answerMode = if (grounded.isFallbackIDontKnow) {
                                    RagAnswerMode.FALLBACK_I_DONT_KNOW
                                } else {
                                    RagAnswerMode.GROUNDED
                                }
                            )
                        )
                    } else if (summary.chunks.isNotEmpty()) {
                        summary.copy(
                            groundedAnswer = GroundedAnswerPayload(
                                answerText = aiResponseText,
                                sources = emptyList(),
                                quotes = emptyList(),
                                answerMode = RagAnswerMode.GROUNDED,
                                pipelineMode = com.example.aiadventchallenge.domain.model.RagPostProcessingMode.valueOf(summary.postProcessingMode),
                                confidence = com.example.aiadventchallenge.domain.model.RagConfidenceSummary(
                                    answerable = true,
                                    reason = null,
                                    minAnswerableChunks = 1,
                                    finalChunkCount = summary.chunks.size
                                )
                            )
                        )
                    } else {
                        summary
                    }
                }
                val executionInfo = buildExecutionInfo(
                    backendSettings = backendSettings,
                    answerMode = answerMode,
                    latencyMs = latencyMs,
                    retrievalLatencyMs = retrievalLatencyMs,
                    generationLatencyMs = latencyMs,
                    retrievalSummary = updatedSummary,
                    messageId = aiMessage.id,
                    requestConfig = config,
                    responseChars = aiResponseText.length,
                    totalTokens = answerWithUsage.totalTokens
                )
                
                ChatMessageResult.Success(
                    userMessage = null,
                    aiMessage = aiMessage,
                    aiResponse = aiResponseText,
                    retrievalSummary = updatedSummary,
                    executionInfo = executionInfo,
                    answerPresentation = buildAnswerPresentation(
                        aiMessage = aiMessage,
                        executionInfo = executionInfo,
                        retrievalSummary = updatedSummary
                    )
                )
            }
            is ChatResult.Error -> {
                Log.e(TAG, "❌ AI response error: ${result.message}")
                ChatMessageResult.Error(result.message, null)
            }
            null -> ChatMessageResult.Error("Не удалось получить ответ от модели.", null)
        }
    }

    private fun buildExecutionInfo(
        backendSettings: AiBackendSettings,
        answerMode: AnswerMode,
        latencyMs: Long,
        retrievalLatencyMs: Long? = null,
        generationLatencyMs: Long? = null,
        retrievalSummary: com.example.aiadventchallenge.domain.mcp.RetrievalSummary?,
        messageId: String? = null,
        errorMessage: String? = null,
        requestConfig: RequestConfig,
        responseChars: Int? = null,
        totalTokens: Int? = null
    ): ChatExecutionInfo {
        return ChatExecutionInfo(
            messageId = messageId,
            backend = backendSettings.selectedBackend,
            answerMode = answerMode,
            ragEnabled = answerMode != AnswerMode.PLAIN_LLM,
            latencyMs = latencyMs,
            retrievalLatencyMs = retrievalLatencyMs,
            generationLatencyMs = generationLatencyMs,
            model = when (backendSettings.selectedBackend) {
                AiBackendType.LOCAL_OLLAMA -> backendSettings.localConfig.model
                AiBackendType.PRIVATE_AI_SERVICE -> requestConfig.modelId ?: backendSettings.privateServiceConfig.model
                AiBackendType.REMOTE -> requestConfig.modelId
            },
            profile = requestConfig.localLlmProfile,
            promptProfile = requestConfig.promptProfile,
            responseChars = responseChars,
            totalTokens = totalTokens,
            numCtx = requestConfig.numCtx,
            selectedSourceCount = retrievalSummary?.chunks?.size ?: 0,
            errorCategory = errorMessage?.let {
                inferFailureCategory(
                    message = it,
                    backendSettings = backendSettings,
                    retrievalSummary = retrievalSummary
                )
            },
            errorMessage = errorMessage
        )
    }

    private fun buildAnswerPresentation(
        aiMessage: ChatMessage,
        executionInfo: ChatExecutionInfo,
        retrievalSummary: com.example.aiadventchallenge.domain.mcp.RetrievalSummary?
    ): ChatAnswerPresentation {
        val sources = retrievalSummary
            ?.groundedAnswer
            ?.sources
            ?.take(3)
            ?.map { source ->
                ChatSourcePreview(
                    title = source.title ?: source.relativePath ?: source.source ?: "Источник",
                    subtitle = listOfNotNull(source.section, source.relativePath).joinToString(" • "),
                    score = source.similarityScore ?: source.rerankScore
                )
            }
            ?.takeIf { it.isNotEmpty() }
            ?: retrievalSummary
                ?.chunks
                ?.take(3)
                ?.map { chunk ->
                    ChatSourcePreview(
                        title = chunk.title.ifBlank { chunk.relativePath },
                        subtitle = listOf(chunk.section, chunk.relativePath)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        score = chunk.rerankScore ?: chunk.score
                    )
                }
                .orEmpty()

        return ChatAnswerPresentation(
            messageId = aiMessage.id,
            executionInfo = executionInfo,
            sources = sources,
            retrievalSummary = retrievalSummary
        )
    }

    private fun inferFailureCategory(
        message: String,
        backendSettings: AiBackendSettings,
        retrievalSummary: com.example.aiadventchallenge.domain.mcp.RetrievalSummary?
    ): ChatFailureCategory {
        val normalized = message.lowercase()
        return when {
            normalized.contains("timeout") || normalized.contains("вовремя") -> ChatFailureCategory.TIMEOUT
            normalized.contains("unexpected") || normalized.contains("неожиданном формате") || normalized.contains("malformed") ->
                ChatFailureCategory.MALFORMED_RESPONSE
            normalized.contains("пуст") -> ChatFailureCategory.EMPTY_RESPONSE
            normalized.contains("retrieval") || normalized.contains("mcp") || normalized.contains("index") ->
                ChatFailureCategory.RETRIEVAL_UNAVAILABLE
            backendSettings.selectedBackend == com.example.aiadventchallenge.domain.model.AiBackendType.LOCAL_OLLAMA &&
                (normalized.contains("ollama") || normalized.contains("локальн")) ->
                ChatFailureCategory.LOCAL_MODEL_UNAVAILABLE
            backendSettings.selectedBackend == AiBackendType.PRIVATE_AI_SERVICE &&
                (normalized.contains("private ai service") || normalized.contains("gateway") || normalized.contains("api key")) ->
                ChatFailureCategory.REMOTE_MODEL_UNAVAILABLE
            backendSettings.selectedBackend == com.example.aiadventchallenge.domain.model.AiBackendType.REMOTE ->
                ChatFailureCategory.REMOTE_MODEL_UNAVAILABLE
            retrievalSummary != null && retrievalSummary.chunks.isEmpty() -> ChatFailureCategory.RETRIEVAL_EMPTY
            else -> ChatFailureCategory.UNKNOWN
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

    private fun sanitizeAnswerTextForDisplay(answerText: String): String {
        return answerText
            .trimEnd()
            .replace(numberedSourcesBlockRegex, "")
            .replace(fallbackSourcesBlockRegex, "")
            .replace(englishSourcesBlockRegex, "")
            .replace(englishInlineSourcesRegex, "")
            .trimEnd()
    }

    private fun sanitizeMessagesForLlmContext(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.map { message ->
            if (message.isFromUser) {
                message
            } else {
                message.copy(content = sanitizeAssistantContentForLlm(message.content))
            }
        }
    }

    private fun sanitizeAssistantContentForLlm(content: String): String {
        return content
            .trimEnd()
            .replace(numberedSourcesBlockRegex, "")
            .replace(fallbackSourcesBlockRegex, "")
            .trimEnd()
    }

    private fun buildRagEnhancedMessages(
        userPrompt: String,
        systemPrompt: String
    ): List<Message> {
        return listOf(
            Message(
                role = com.example.aiadventchallenge.data.model.MessageRole.SYSTEM,
                content = systemPrompt
            ),
            Message(
                role = com.example.aiadventchallenge.data.model.MessageRole.USER,
                content = userPrompt
            )
        )
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
