package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.config.JsonConfig
import com.example.aiadventchallenge.data.local.dao.ConversationTaskStateDao
import com.example.aiadventchallenge.data.local.entity.ConversationTaskStateEntity
import com.example.aiadventchallenge.domain.repository.TaskStateRepository
import com.example.aiadventchallenge.rag.memory.ConversationTaskState

class TaskStateRepositoryImpl(
    private val dao: ConversationTaskStateDao
) : TaskStateRepository {

    override suspend fun getTaskState(branchId: String): ConversationTaskState? {
        return dao.getByBranchId(branchId)
            ?.payloadJson
            ?.takeIf { it.isNotBlank() }
            ?.let { JsonConfig.json.decodeFromString<ConversationTaskState>(it) }
    }

    override suspend fun upsertTaskState(branchId: String, state: ConversationTaskState) {
        dao.upsert(
            ConversationTaskStateEntity(
                branchId = branchId,
                payloadJson = JsonConfig.json.encodeToString(ConversationTaskState.serializer(), state),
                updatedAt = state.updatedAt
            )
        )
    }

    override suspend fun deleteByBranch(branchId: String) {
        dao.deleteByBranchId(branchId)
    }

    override suspend fun copyTaskState(fromBranchId: String, toBranchId: String) {
        val state = getTaskState(fromBranchId) ?: return
        upsertTaskState(
            branchId = toBranchId,
            state = state.copy(updatedAt = System.currentTimeMillis())
        )
    }

    override suspend fun clearAll() {
        dao.deleteAll()
    }
}
