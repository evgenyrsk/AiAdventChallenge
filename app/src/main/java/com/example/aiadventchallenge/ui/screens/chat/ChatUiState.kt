package com.example.aiadventchallenge.ui.screens.chat

import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.AiBackendType
import com.example.aiadventchallenge.domain.model.ChatAnswerPresentation
import com.example.aiadventchallenge.domain.model.ChatExecutionInfo
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.RagComparisonResult
import com.example.aiadventchallenge.domain.model.RagEvaluationRunResult
import com.example.aiadventchallenge.rag.memory.ConversationTaskState

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
    val selectedBackend: AiBackendType = AiBackendType.REMOTE,
    val localLlmConfig: LocalLlmConfig = LocalLlmConfig(),
    val answerMode: AnswerMode = AnswerMode.PLAIN_LLM,
    val latestRetrievalSummary: RetrievalSummary? = null,
    val latestTaskState: ConversationTaskState? = null,
    val latestExecutionInfo: ChatExecutionInfo? = null,
    val latestAnswerPresentation: ChatAnswerPresentation? = null,
    val latestComparisonResult: RagComparisonResult? = null,
    val latestEvaluationResult: RagEvaluationRunResult? = null,
    val isComparisonRunning: Boolean = false,
    val isEvaluationRunning: Boolean = false
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
