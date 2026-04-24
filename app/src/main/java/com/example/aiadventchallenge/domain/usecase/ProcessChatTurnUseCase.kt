package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.data.mcp.FitnessRagConfig
import com.example.aiadventchallenge.domain.chat.ChatMessageHandler
import com.example.aiadventchallenge.domain.chat.ChatMessageResult
import com.example.aiadventchallenge.domain.llm.LocalLlmProfileResolver
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.ChatSettingsRepository
import com.example.aiadventchallenge.domain.repository.TaskStateRepository
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import com.example.aiadventchallenge.rag.memory.RagConversationContext
import com.example.aiadventchallenge.rag.memory.TaskStateUpdater

class ProcessChatTurnUseCase(
    private val chatRepository: ChatRepository,
    private val branchRepository: BranchRepository,
    private val taskStateRepository: TaskStateRepository,
    private val chatSettingsRepository: ChatSettingsRepository,
    private val taskStateUpdater: TaskStateUpdater,
    private val chatMessageHandler: ChatMessageHandler,
    private val prepareRagRequestUseCase: PrepareRagRequestUseCase,
    private val localLlmProfileResolver: LocalLlmProfileResolver
) {

    suspend operator fun invoke(
        userInput: String,
        fitnessProfile: FitnessProfileType,
        activeBranchId: String,
        parentMessageId: String?,
        mcpContext: String?,
        answerMode: AnswerMode
    ): ProcessChatTurnResult {
        if (answerMode != AnswerMode.RAG_ENHANCED) {
            val userMessage = chatMessageHandler.saveUserMessage(
                userInput = userInput,
                activeBranchId = activeBranchId,
                parentMessageId = parentMessageId
            )
            branchRepository.updateLastMessage(activeBranchId, userMessage.id)

            val result = chatMessageHandler.generateAiResponse(
                userInput = userInput,
                fitnessProfile = fitnessProfile,
                activeBranchId = activeBranchId,
                parentMessageId = userMessage.id,
                mcpContext = mcpContext,
                answerMode = answerMode
            )

            val aiMessageId = (result as? ChatMessageResult.Success)?.aiMessage?.id
            if (aiMessageId != null) {
                branchRepository.updateLastMessage(activeBranchId, aiMessageId)
            }

            return ProcessChatTurnResult(
                result = result,
                taskState = taskStateRepository.getTaskState(activeBranchId),
                retrievalSummary = (result as? ChatMessageResult.Success)?.retrievalSummary
            )
        }

        val history = chatRepository.getMessagesByBranch(activeBranchId)
        val previousTaskState = taskStateRepository.getTaskState(activeBranchId)
        val updatedTaskState = taskStateUpdater.update(
            previousState = previousTaskState,
            recentMessages = history.takeLast(6).map(ChatMessage::content),
            newUserMessage = userInput
        )

        val userMessage = chatMessageHandler.saveUserMessage(
            userInput = userInput,
            activeBranchId = activeBranchId,
            parentMessageId = parentMessageId
        )
        branchRepository.updateLastMessage(activeBranchId, userMessage.id)

        taskStateRepository.upsertTaskState(activeBranchId, updatedTaskState)

        val backendSettings = chatSettingsRepository.getAiBackendSettings()
        val promptProfile = localLlmProfileResolver.resolveExecutionSettings(
            localConfig = backendSettings.localConfig,
            answerMode = answerMode
        ).promptProfile

        val preparedRagRequest = prepareRagRequestUseCase(
            question = userInput,
            config = FitnessRagConfig.enhancedPipeline,
            conversationContext = RagConversationContext(
                taskState = updatedTaskState,
                recentMessages = history.takeLast(4).map(ChatMessage::content)
            ),
            promptProfile = promptProfile
        )

        val result = chatMessageHandler.generateAiResponse(
            userInput = userInput,
            fitnessProfile = fitnessProfile,
            activeBranchId = activeBranchId,
            parentMessageId = userMessage.id,
            mcpContext = mcpContext,
            answerMode = answerMode,
            preparedRagRequest = preparedRagRequest
        )

        val aiMessageId = (result as? ChatMessageResult.Success)?.aiMessage?.id
        if (aiMessageId != null) {
            branchRepository.updateLastMessage(activeBranchId, aiMessageId)
        }

        return ProcessChatTurnResult(
            result = result,
            taskState = updatedTaskState,
            retrievalSummary = (result as? ChatMessageResult.Success)?.retrievalSummary
        )
    }
}

data class ProcessChatTurnResult(
    val result: ChatMessageResult,
    val taskState: ConversationTaskState? = null,
    val retrievalSummary: RetrievalSummary? = null
)
