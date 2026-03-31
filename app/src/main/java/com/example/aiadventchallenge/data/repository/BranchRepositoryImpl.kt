package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.BranchDao
import com.example.aiadventchallenge.data.local.entity.BranchEntity
import com.example.aiadventchallenge.domain.model.ChatBranch
import com.example.aiadventchallenge.domain.repository.BranchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BranchRepositoryImpl(
    private val branchDao: BranchDao
) : BranchRepository {

    override fun getAllBranches(): Flow<List<ChatBranch>> {
        return branchDao.getAllBranches().map { entities ->
            entities.map { entity ->
                ChatBranch(
                    id = entity.id,
                    parentBranchId = entity.parentBranchId,
                    checkpointMessageId = entity.checkpointMessageId,
                    lastMessageId = entity.lastMessageId,
                    title = entity.title,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    override fun getBranchById(branchId: String): Flow<ChatBranch?> {
        return kotlinx.coroutines.flow.flow {
            val entity = branchDao.getBranchById(branchId)
            if (entity != null) {
                emit(
                    ChatBranch(
                        id = entity.id,
                        parentBranchId = entity.parentBranchId,
                        checkpointMessageId = entity.checkpointMessageId,
                        lastMessageId = entity.lastMessageId,
                        title = entity.title,
                        createdAt = entity.createdAt
                    )
                )
            } else {
                emit(null)
            }
        }
    }

    override suspend fun getActiveBranchId(): String? {
        return branchDao.getActiveBranchId()
    }

    override suspend fun setActiveBranchId(branchId: String) {
        branchDao.deactivateAllBranches()
        branchDao.activateBranch(branchId)
    }

    override suspend fun createBranch(branch: ChatBranch) {
        val entity = BranchEntity(
            id = branch.id,
            parentBranchId = branch.parentBranchId,
            checkpointMessageId = branch.checkpointMessageId,
            lastMessageId = branch.lastMessageId,
            title = branch.title,
            createdAt = branch.createdAt,
            isActive = false
        )
        branchDao.insertBranch(entity)
    }

    override suspend fun updateBranch(branch: ChatBranch) {
        val existing = branchDao.getBranchById(branch.id) ?: return
        val entity = BranchEntity(
            id = branch.id,
            parentBranchId = branch.parentBranchId,
            checkpointMessageId = branch.checkpointMessageId,
            lastMessageId = branch.lastMessageId,
            title = branch.title,
            createdAt = branch.createdAt,
            isActive = existing.isActive
        )
        branchDao.updateBranch(entity)
    }

    override suspend fun updateLastMessage(branchId: String, messageId: String) {
        branchDao.updateLastMessage(branchId, messageId)
    }

    override suspend fun getLastMessageId(branchId: String): String? {
        return branchDao.getLastMessageId(branchId)
    }

    override suspend fun deleteBranch(branchId: String) {
        branchDao.deleteBranch(branchId)
    }

    override suspend fun clearAllBranches() {
        branchDao.clearAllBranches()
    }
}
