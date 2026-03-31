package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.model.ChatBranch
import kotlinx.coroutines.flow.Flow

interface BranchRepository {
    fun getAllBranches(): Flow<List<ChatBranch>>

    fun getBranchById(branchId: String): Flow<ChatBranch?>

    suspend fun getActiveBranchId(): String?

    suspend fun setActiveBranchId(branchId: String)

    suspend fun createBranch(branch: ChatBranch)

    suspend fun updateBranch(branch: ChatBranch)

    suspend fun deleteBranch(branchId: String)

    suspend fun clearAllBranches()

    suspend fun updateLastMessage(branchId: String, messageId: String)

    suspend fun getLastMessageId(branchId: String): String?
}
