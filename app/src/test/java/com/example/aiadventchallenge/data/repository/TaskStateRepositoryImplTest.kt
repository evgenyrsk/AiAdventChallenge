package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.ConversationTaskStateDao
import com.example.aiadventchallenge.data.local.entity.ConversationTaskStateEntity
import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TaskStateRepositoryImplTest {

    @Test
    fun `repository persists and copies task state`() = runTest {
        val dao = FakeConversationTaskStateDao()
        val repository = TaskStateRepositoryImpl(dao)
        val state = ConversationTaskState(
            dialogGoal = "Снижение веса",
            resolvedConstraints = listOf("Мало времени"),
            latestSummary = "Нужен устойчивый режим"
        )

        repository.upsertTaskState("main", state)
        repository.copyTaskState("main", "branch_1")

        val restoredMain = repository.getTaskState("main")
        val restoredBranch = repository.getTaskState("branch_1")

        assertEquals("Снижение веса", restoredMain?.dialogGoal)
        assertNotNull(restoredBranch)
        assertEquals(restoredMain?.dialogGoal, restoredBranch?.dialogGoal)
        assertEquals(restoredMain?.resolvedConstraints, restoredBranch?.resolvedConstraints)
    }

    private class FakeConversationTaskStateDao : ConversationTaskStateDao {
        private val storage = mutableMapOf<String, ConversationTaskStateEntity>()

        override suspend fun getByBranchId(branchId: String): ConversationTaskStateEntity? = storage[branchId]

        override suspend fun upsert(entity: ConversationTaskStateEntity) {
            storage[entity.branchId] = entity
        }

        override suspend fun deleteByBranchId(branchId: String) {
            storage.remove(branchId)
        }

        override suspend fun deleteAll() {
            storage.clear()
        }
    }
}
