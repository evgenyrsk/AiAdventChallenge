package com.example.aiadventchallenge.domain.chat

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.PreparedRagRequest
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.rag.memory.ConversationTaskState

/**
 * Обработчик сообщений чата.
 * 
 * Отвечает за:
 * - Создание ChatMessage объектов
 * - Сохранение сообщений в репозиторий
 * - Оркестрацию LLM запросов
 * - Обработку результатов LLM (успех, ошибка, пустой ответ)
 */
interface ChatMessageHandler {
    /**
     * Обрабатывает пользовательское сообщение и вызывает LLM.
     * 
     * @param userInput Ввод пользователя
     * @param fitnessProfile Фитнес-профиль пользователя
     * @param activeBranchId ID активной ветки
     * @param parentMessageId ID родительского сообщения
     * @param mcpContext Дополнительный контекст от MCP tools (опционально)
     * @return Результат обработки с сообщениями и AI ответом
     */
    suspend fun handleUserMessage(
        userInput: String,
        fitnessProfile: FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String? = null,
        answerMode: AnswerMode = AnswerMode.PLAIN_LLM
    ): ChatMessageResult
    
    suspend fun saveUserMessage(
        userInput: String,
        activeBranchId: String,
        parentMessageId: String?
    ): ChatMessage
    
    suspend fun generateAiResponse(
        userInput: String,
        fitnessProfile: FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String?,
        answerMode: AnswerMode = AnswerMode.PLAIN_LLM,
        preparedRagRequest: PreparedRagRequest? = null
    ): ChatMessageResult
    
    suspend fun handleSystemPrompt(systemPrompt: String): SystemPromptResult
}

sealed class ChatMessageResult {
    data class Success(
        val userMessage: ChatMessage?,
        val aiMessage: ChatMessage?,
        val aiResponse: String,
        val retrievalSummary: RetrievalSummary? = null,
        val taskStateSnapshot: ConversationTaskState? = null
    ) : ChatMessageResult()
    
    data class Error(
        val errorMessage: String,
        val userMessage: ChatMessage?
    ) : ChatMessageResult()
    
    data class EmptyResponse(
        val errorMessage: String,
        val userMessage: ChatMessage?
    ) : ChatMessageResult()
}

sealed class SystemPromptResult {
    data class Success(val message: String) : SystemPromptResult()
    data class Error(val errorMessage: String) : SystemPromptResult()
}
