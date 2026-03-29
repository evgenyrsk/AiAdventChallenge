package com.example.aiadventchallenge.ui.screens.chat

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class ChatUiStateTest {

    @Test
    fun `canCreateBranch returns false when not branching strategy`() {
        val state = ChatUiState(
            isBranchingStrategy = false,
            messages = listOf(ChatMessage("1", "Test", true))
        )
        
        assertFalse(state.canCreateBranch)
    }

    @Test
    fun `canCreateBranch returns false when no messages`() {
        val state = ChatUiState(
            isBranchingStrategy = true,
            messages = emptyList()
        )
        
        assertFalse(state.canCreateBranch)
    }

    @Test
    fun `canCreateBranch returns true when branching strategy and has messages`() {
        val state = ChatUiState(
            isBranchingStrategy = true,
            messages = listOf(ChatMessage("1", "Test", true))
        )
        
        assertTrue(state.canCreateBranch)
    }

    @Test
    fun `hasMultipleBranches returns true when more than one branch`() {
        val state = ChatUiState(
            availableBranches = listOf(
                BranchUiModel("1", "Main", true, null, "msg1", null, Date().time),
                BranchUiModel("2", "Branch 1", false, "1", "msg1", null, Date().time)
            )
        )
        
        assertTrue(state.hasMultipleBranches)
    }

    @Test
    fun `hasMultipleBranches returns false when only one branch`() {
        val state = ChatUiState(
            availableBranches = listOf(
                BranchUiModel("1", "Main", true, null, "msg1", null, Date().time)
            )
        )
        
        assertFalse(state.hasMultipleBranches)
    }

    @Test
    fun `isRootBranch returns true when activeBranchId is null`() {
        val state = ChatUiState(
            activeBranchId = null
        )
        
        assertTrue(state.isRootBranch)
    }

    @Test
    fun `isRootBranch returns true when activeBranchId is main`() {
        val state = ChatUiState(
            activeBranchId = "main"
        )
        
        assertTrue(state.isRootBranch)
    }

    @Test
    fun `isRootBranch returns false when activeBranchId is not main`() {
        val state = ChatUiState(
            activeBranchId = "branch_123"
        )
        
        assertFalse(state.isRootBranch)
    }

    @Test
    fun `getMessageBranchCount returns correct count`() {
        val state = ChatUiState(
            availableBranches = listOf(
                BranchUiModel("1", "Main", true, null, null, null, Date().time),
                BranchUiModel("2", "Branch 1", false, "1", "msg1", null, Date().time),
                BranchUiModel("3", "Branch 2", false, "1", "msg2", null, Date().time)
            )
        )
        
        assertEquals(1, state.getMessageBranchCount("msg1"))
        assertEquals(1, state.getMessageBranchCount("msg2"))
        assertEquals(0, state.getMessageBranchCount("msg3"))
    }

    @Test
    fun `getBranchesForMessage returns correct branches`() {
        val state = ChatUiState(
            availableBranches = listOf(
                BranchUiModel("1", "Main", true, null, null, null, Date().time),
                BranchUiModel("2", "Branch 1", false, "1", "msg1", null, Date().time),
                BranchUiModel("3", "Branch 2", false, "1", "msg2", null, Date().time)
            )
        )
        
        val branchesForMsg1 = state.getBranchesForMessage("msg1")
        assertEquals(1, branchesForMsg1.size)
        assertEquals("Branch 1", branchesForMsg1[0].title)
        
        val branchesForMsg2 = state.getBranchesForMessage("msg2")
        assertEquals(1, branchesForMsg2.size)
        assertEquals("Branch 2", branchesForMsg2[0].title)
        
        val branchesForMsg3 = state.getBranchesForMessage("msg3")
        assertTrue(branchesForMsg3.isEmpty())
    }
}