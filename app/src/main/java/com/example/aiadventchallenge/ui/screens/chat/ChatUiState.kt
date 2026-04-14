package com.example.aiadventchallenge.ui.screens.chat

import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.FitnessProfileType

data class ChatUiState(
    val isLoading: Boolean = false,

    val isBranchingStrategy: Boolean = false,
    val activeBranchId: String? = null,
    val activeBranchName: String? = null,
    val availableBranches: List<BranchUiModel> = emptyList(),

    val showBranchPicker: Boolean = false,
    val showCreateBranchDialog: Boolean = false,

    val branchCreationTargetMessageId: String? = null,
    val branchCreationTargetPreview: String? = null,
    val newBranchName: String = "",
    val newBranchError: String? = null,

    val currentBranchCheckpointMessageId: String? = null,

    val showBranchActionsForMessageId: String? = null,
    val branchesForMessage: List<BranchUiModel> = emptyList(),

    val fitnessProfile: FitnessProfileType = FitnessProfileType.INTERMEDIATE,
    val latestRetrievalSummary: RetrievalSummary? = null
) {
    val hasMultipleBranches: Boolean
        get() = availableBranches.size > 1

    val isRootBranch: Boolean
        get() = activeBranchId == null || activeBranchId == "main"

    fun getMessageBranchCount(messageId: String): Int {
        return availableBranches.count { it.checkpointMessageId == messageId }
    }

    fun getBranchesForMessage(messageId: String): List<BranchUiModel> {
        return availableBranches.filter { it.checkpointMessageId == messageId }
    }
}
