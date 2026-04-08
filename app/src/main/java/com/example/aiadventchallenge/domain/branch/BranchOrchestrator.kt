package com.example.aiadventchallenge.domain.branch

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.ui.screens.chat.BranchUiModel

/**
 * Оркестратор управления ветками диалога.
 * 
 * Отвечает за:
 * - Создание новых веток из сообщения
 * - Удаление веток
 * - Переключение между ветками
 * - Получение списка веток
 * - Получение веток для конкретного сообщения
 */
interface BranchOrchestrator {
    /**
     * Создает новую ветку из сообщения.
     * 
     * @param messageId ID сообщения, от которого создается ветка
     * @param branchName Название новой ветки
     * @param switchToNew Переключиться ли на новую ветку сразу после создания
     * @return Result с результатом создания
     */
    suspend fun createBranchFromMessage(
        messageId: String,
        branchName: String,
        switchToNew: Boolean = true
    ): BranchCreationResult
    
    /**
     * Удаляет ветку.
     * 
     * @param branchId ID ветки для удаления
     */
    suspend fun deleteBranch(branchId: String)
    
    /**
     * Переключается на указанную ветку.
     * 
     * @param branchId ID ветки для переключения
     * @return Result с сообщениями ветки
     */
    suspend fun switchToBranch(branchId: String): BranchSwitchResult
    
    /**
     * Получает все ветки.
     * 
     * @return Список всех веток
     */
    suspend fun getAllBranches(): List<BranchUiModel>
    
    /**
     * Получает ветки, созданные из указанного сообщения.
     * 
     * @param messageId ID сообщения
     * @param allBranches Все доступные ветки
     * @return Список веток для сообщения
     */
    fun getBranchesForMessage(
        messageId: String,
        allBranches: List<BranchUiModel>
    ): List<BranchUiModel>
}

sealed class BranchCreationResult {
    data class Success(
        val branchId: String,
        val messages: List<ChatMessage>,
        val checkpointMessageId: String
    ) : BranchCreationResult()
    
    data class Error(
        val message: String,
        val type: BranchCreationErrorType
    ) : BranchCreationResult()
}

enum class BranchCreationErrorType {
    EMPTY_NAME,
    NO_MESSAGE_SELECTED,
    UNKNOWN
}

sealed class BranchSwitchResult {
    data class Success(
        val messages: List<ChatMessage>,
        val branchName: String,
        val checkpointMessageId: String?
    ) : BranchSwitchResult()
    
    data class Error(val message: String) : BranchSwitchResult()
}
