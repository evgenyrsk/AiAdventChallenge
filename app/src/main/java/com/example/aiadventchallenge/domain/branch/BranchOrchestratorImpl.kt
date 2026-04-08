package com.example.aiadventchallenge.domain.branch

import android.util.Log
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatBranch
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.ui.screens.chat.BranchUiModel

class BranchOrchestratorImpl(
    private val branchRepository: BranchRepository,
    private val chatRepository: ChatRepository
) : BranchOrchestrator {
    
    private val TAG = "BranchOrchestrator"
    
    override suspend fun createBranchFromMessage(
        messageId: String,
        branchName: String,
        switchToNew: Boolean
    ): BranchCreationResult {
        Log.d(TAG, "=== Creating Branch ===")
        Log.d(TAG, "Message ID: $messageId")
        Log.d(TAG, "Branch name: $branchName")
        Log.d(TAG, "Switch to new: $switchToNew")
        
        // Валидация
        if (branchName.isBlank()) {
            Log.w(TAG, "Branch name is empty")
            return BranchCreationResult.Error(
                message = "Название ветки не может быть пустым",
                type = BranchCreationErrorType.EMPTY_NAME
            )
        }
        
        // Получаем активную ветку и создаем новую
        val activeBranchId = branchRepository.getActiveBranchId() ?: "main"
        val newBranchId = "branch_${System.currentTimeMillis()}"
        
        Log.d(TAG, "Active branch ID: $activeBranchId")
        Log.d(TAG, "New branch ID: $newBranchId")
        
        val newBranch = ChatBranch(
            id = newBranchId,
            parentBranchId = activeBranchId,
            checkpointMessageId = messageId,
            lastMessageId = messageId,
            title = branchName,
            createdAt = System.currentTimeMillis()
        )
        
        branchRepository.createBranch(newBranch)
        Log.d(TAG, "Branch created successfully")
        
        // Переключаемся на новую ветку если нужно
        if (switchToNew) {
            branchRepository.setActiveBranchId(newBranchId)
            Log.d(TAG, "Switched to new branch")
            
            val fullPath = chatRepository.getBranchPathWithCheckpoint(newBranchId)
            
            return BranchCreationResult.Success(
                branchId = newBranchId,
                messages = fullPath,
                checkpointMessageId = messageId
            )
        }
        
        // Возвращаем информацию о созданной ветке, но не переключаемся
        val fullPath = chatRepository.getBranchPathWithCheckpoint(newBranchId)
        
        return BranchCreationResult.Success(
            branchId = newBranchId,
            messages = fullPath,
            checkpointMessageId = messageId
        )
    }
    
    override suspend fun deleteBranch(branchId: String) {
        Log.d(TAG, "=== Deleting Branch ===")
        Log.d(TAG, "Branch ID: $branchId")
        
        branchRepository.deleteBranch(branchId)
        Log.d(TAG, "Branch deleted successfully")
        Log.d(TAG, "=== Branch Deleted ===\n")
    }
    
    override suspend fun switchToBranch(branchId: String): BranchSwitchResult {
        Log.d(TAG, "=== Switching to Branch ===")
        Log.d(TAG, "Branch ID: $branchId")
        
        try {
            branchRepository.setActiveBranchId(branchId)
            Log.d(TAG, "Set active branch ID: $branchId")
            
            val fullPath = chatRepository.getBranchPathWithCheckpoint(branchId)
            Log.d(TAG, "Loaded ${fullPath.size} messages from branch")
            
            val branchFlow = branchRepository.getBranchById(branchId)
            val branchList = mutableListOf<ChatBranch>()
            branchFlow.collect {
                if (it != null) {
                    branchList.add(it)
                }
            }
            val activeBranch = branchList.firstOrNull() ?: run {
                Log.e(TAG, "Branch not found: $branchId")
                throw IllegalArgumentException("Branch not found: $branchId")
            }
            
            Log.d(TAG, "Branch name: ${activeBranch.title}")
            Log.d(TAG, "Checkpoint message ID: ${activeBranch.checkpointMessageId}")
            
            Log.d(TAG, "=== Branch Switched ===\n")
            
            return BranchSwitchResult.Success(
                messages = fullPath,
                branchName = activeBranch.title,
                checkpointMessageId = activeBranch.checkpointMessageId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch to branch", e)
            return BranchSwitchResult.Error(
                message = "Ошибка переключения ветки: ${e.message}"
            )
        }
    }
    
    override suspend fun getAllBranches(): List<BranchUiModel> {
        val branches = branchRepository.getAllBranches()
        val activeBranchId = branchRepository.getActiveBranchId()
        
        val branchesList = mutableListOf<ChatBranch>()
        branches.collect {
            branchesList.addAll(it)
        }
        
        return branchesList.map { branch ->
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
    }
    
    override fun getBranchesForMessage(
        messageId: String,
        allBranches: List<BranchUiModel>
    ): List<BranchUiModel> {
        return allBranches.filter { it.checkpointMessageId == messageId }
    }
}
