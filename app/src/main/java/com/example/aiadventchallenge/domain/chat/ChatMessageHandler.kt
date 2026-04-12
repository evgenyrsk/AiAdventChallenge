package com.example.aiadventchallenge.domain.chat

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatResult
import com.example.aiadventchallenge.domain.model.FitnessProfileType

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
        mcpContext: String? = null
    ): ChatMessageResult
}

sealed class ChatMessageResult {
    data class Success(
        val userMessage: ChatMessage,
        val aiMessage: ChatMessage,
        val aiResponse: String
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
