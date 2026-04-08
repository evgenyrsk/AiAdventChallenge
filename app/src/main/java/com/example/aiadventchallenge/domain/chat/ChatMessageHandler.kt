package com.example.aiadventchallenge.domain.chat

import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult

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
     * @param taskContext Контекст задачи (может быть null)
     * @param fitnessProfile Фитнес-профиль пользователя
     * @param activeBranchId ID активной ветки
     * @param parentMessageId ID родительского сообщения
     * @param mcpContext Дополнительный контекст от MCP tools (опционально)
     * @return Результат обработки с сообщениями и AI ответом
     */
    suspend fun handleUserMessage(
        userInput: String,
        taskContext: com.example.aiadventchallenge.domain.model.TaskContext?,
        fitnessProfile: com.example.aiadventchallenge.domain.model.FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String? = null
    ): ChatMessageResult
}

sealed class ChatMessageResult {
    data class Success(
        val userMessage: ChatMessage,
        val aiMessage: ChatMessage,
        val aiResponse: EnhancedTaskAiResponse
    ) : ChatMessageResult()
    
    data class Error(
        val errorMessage: String,
        val userMessage: ChatMessage
    ) : ChatMessageResult()
    
    data class EmptyResponse(
        val errorMessage: String,
        val userMessage: ChatMessage
    ) : ChatMessageResult()
}
