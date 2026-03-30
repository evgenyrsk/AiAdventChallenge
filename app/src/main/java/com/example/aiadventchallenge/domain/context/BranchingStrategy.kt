package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ChatBranch
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.data.repository.ChatRepository
import kotlinx.coroutines.flow.first

class BranchingStrategy(
    private val config: ContextStrategyConfig,
    private val branchRepository: BranchRepository,
    private val chatRepository: ChatRepository
) : ContextStrategy {

    suspend fun initialize() {
        val activeBranchId = branchRepository.getActiveBranchId()
        if (activeBranchId == null) {
            val mainBranch = ChatBranch(
                id = "main",
                parentBranchId = null,
                checkpointMessageId = "",
                title = "Main",
                createdAt = System.currentTimeMillis()
            )
            branchRepository.createBranch(mainBranch)
            branchRepository.setActiveBranchId("main")
        }
    }

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        val activeBranchId = branchRepository.getActiveBranchId()
        val branchMessages = if (activeBranchId != null) {
            messages.filter { it.branchId == activeBranchId }
        } else {
            messages
        }

        branchMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    override suspend fun onUserMessage(message: ChatMessage) {
    }

    override suspend fun onAssistantMessage(message: ChatMessage) {
    }

    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "strategy" to "Branching",
            "windowSize" to config.windowSize
        )
    }
}
