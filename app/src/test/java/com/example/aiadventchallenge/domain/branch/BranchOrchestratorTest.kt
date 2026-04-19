package com.example.aiadventchallenge.domain.branch

import android.util.Log
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatBranch
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.TaskStateRepository
import com.example.aiadventchallenge.ui.screens.chat.BranchUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BranchOrchestratorTest {
    
    private lateinit var branchRepository: BranchRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var taskStateRepository: TaskStateRepository
    private lateinit var orchestrator: BranchOrchestratorImpl
    
    private val testBranches = listOf(
        ChatBranch(
            id = "branch_1",
            parentBranchId = "main",
            checkpointMessageId = "msg_1",
            lastMessageId = "msg_2",
            title = "Ветка 1",
            createdAt = System.currentTimeMillis()
        ),
        ChatBranch(
            id = "branch_2",
            parentBranchId = "main",
            checkpointMessageId = "msg_1",
            lastMessageId = "msg_3",
            title = "Ветка 2",
            createdAt = System.currentTimeMillis()
        )
    )
    
    private val testMessages = listOf(
        ChatMessage(
            id = "msg_1",
            parentMessageId = null,
            content = "Test message 1",
            isFromUser = true,
            branchId = "main"
        ),
        ChatMessage(
            id = "msg_2",
            parentMessageId = "msg_1",
            content = "Test message 2",
            isFromUser = false,
            branchId = "branch_1"
        )
    )
    
    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        branchRepository = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        taskStateRepository = mockk(relaxed = true)
        orchestrator = BranchOrchestratorImpl(branchRepository, chatRepository, taskStateRepository)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }
    
    @Test
    fun `create branch from message with valid name`() = runTest {
        coEvery { branchRepository.getActiveBranchId() } returns "main"
        coEvery { chatRepository.getBranchPathWithCheckpoint(any()) } returns testMessages
        coEvery { branchRepository.createBranch(any()) } returns Unit
        
        val result = orchestrator.createBranchFromMessage(
            messageId = "msg_1",
            branchName = "Test Branch",
            switchToNew = true
        )
        
        assertTrue(result is BranchCreationResult.Success)
        val successResult = result as BranchCreationResult.Success
        
        assertNotNull(successResult.branchId)
        assertEquals(testMessages, successResult.messages)
        assertEquals("msg_1", successResult.checkpointMessageId)
        
        coVerify(exactly = 1) { branchRepository.createBranch(any()) }
        coVerify(exactly = 1) { branchRepository.setActiveBranchId(any()) }
        coVerify(exactly = 1) { taskStateRepository.copyTaskState("main", any()) }
    }
    
    @Test
    fun `create branch with empty name - should return error`() = runTest {
        val result = orchestrator.createBranchFromMessage(
            messageId = "msg_1",
            branchName = "",  // Пустое название
            switchToNew = true
        )
        
        assertTrue(result is BranchCreationResult.Error)
        val errorResult = result as BranchCreationResult.Error
        
        assertTrue(errorResult.message.contains("пустым"))
        assertEquals(BranchCreationErrorType.EMPTY_NAME, errorResult.type)
        
        coVerify(exactly = 0) { branchRepository.createBranch(any()) }
    }
    
    @Test
    fun `create branch without switching to new branch`() = runTest {
        coEvery { branchRepository.getActiveBranchId() } returns "main"
        coEvery { chatRepository.getBranchPathWithCheckpoint(any()) } returns testMessages
        coEvery { branchRepository.createBranch(any()) } returns Unit
        
        val result = orchestrator.createBranchFromMessage(
            messageId = "msg_1",
            branchName = "Test Branch",
            switchToNew = false  // Не переключаться
        )
        
        assertTrue(result is BranchCreationResult.Success)
        
        // Не должно быть вызова setActiveBranchId
        coVerify(exactly = 0) { branchRepository.setActiveBranchId(any()) }
    }
    
    @Test
    fun `delete branch`() = runTest {
        orchestrator.deleteBranch("branch_1")
        
        coVerify(exactly = 1) { branchRepository.deleteBranch("branch_1") }
        coVerify(exactly = 1) { taskStateRepository.deleteByBranch("branch_1") }
    }
    
    @Test
    fun `switch to branch successfully`() = runTest {
        coEvery { chatRepository.getBranchPathWithCheckpoint("branch_1") } returns testMessages
        coEvery { branchRepository.getBranchById("branch_1") } returns flowOf(testBranches[0])
        coEvery { branchRepository.setActiveBranchId("branch_1") } returns Unit
        
        val result = orchestrator.switchToBranch("branch_1")
        
        assertTrue(result is BranchSwitchResult.Success)
        val successResult = result as BranchSwitchResult.Success
        
        assertEquals(testMessages, successResult.messages)
        assertEquals("Ветка 1", successResult.branchName)
        assertEquals("msg_1", successResult.checkpointMessageId)
        
        coVerify(exactly = 1) { branchRepository.setActiveBranchId("branch_1") }
        coVerify(exactly = 1) { chatRepository.getBranchPathWithCheckpoint("branch_1") }
        coVerify(exactly = 1) { branchRepository.getBranchById("branch_1") }
    }
    
    @Test
    fun `get all branches`() = runTest {
        coEvery { branchRepository.getAllBranches() } returns flowOf(testBranches)
        coEvery { branchRepository.getActiveBranchId() } returns "main"
        coEvery { chatRepository.getMessageById(any()) } returns null
        
        val branches = orchestrator.getAllBranches()
        
        assertEquals(2, branches.size)
        assertEquals("Ветка 1", branches[0].title)
        assertEquals("Ветка 2", branches[1].title)
    }
    
    @Test
    fun `get branches for message`() = runTest {
        val testUiModels = testBranches.map { branch ->
            BranchUiModel.fromDomain(
                id = branch.id,
                title = branch.title,
                isActive = branch.id == "main",
                parentBranchId = branch.parentBranchId,
                checkpointMessageId = branch.checkpointMessageId,
                lastMessageId = branch.lastMessageId,
                lastMessagePreview = null,
                updatedAt = branch.createdAt
            )
        }
        
        val branches = orchestrator.getBranchesForMessage("msg_1", testUiModels)
        
        // Обе ветки созданы из msg_1
        assertEquals(2, branches.size)
        assertEquals("branch_1", branches[0].id)
        assertEquals("branch_2", branches[1].id)
    }
    
    @Test
    fun `get branches for message - no branches`() = runTest {
        val testUiModels = testBranches.map { branch ->
            BranchUiModel.fromDomain(
                id = branch.id,
                title = branch.title,
                isActive = branch.id == "main",
                parentBranchId = branch.parentBranchId,
                checkpointMessageId = branch.checkpointMessageId,
                lastMessageId = branch.lastMessageId,
                lastMessagePreview = null,
                updatedAt = branch.createdAt
            )
        }
        
        val branches = orchestrator.getBranchesForMessage("msg_999", testUiModels)
        
        // Нет веток, созданных из msg_999
        assertEquals(0, branches.size)
    }
}
