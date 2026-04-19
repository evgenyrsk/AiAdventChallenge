package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.rag.memory.ConversationTaskState

interface TaskStateRepository {
    suspend fun getTaskState(branchId: String): ConversationTaskState?
    suspend fun upsertTaskState(branchId: String, state: ConversationTaskState)
    suspend fun deleteByBranch(branchId: String)
    suspend fun copyTaskState(fromBranchId: String, toBranchId: String)
    suspend fun clearAll()
}
