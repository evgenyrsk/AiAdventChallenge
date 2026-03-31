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

    private var activeBranchId: String? = null
    private var totalMessages: Int = 0
    private var messagesInContext: Int = 0

    suspend fun initialize() {
        val activeBranchId = branchRepository.getActiveBranchId()
        if (activeBranchId == null) {
            val mainBranch = ChatBranch(
                id = "main",
                parentBranchId = null,
                checkpointMessageId = "",
                lastMessageId = null,
                title = "Main",
                createdAt = System.currentTimeMillis()
            )
            branchRepository.createBranch(mainBranch)
            branchRepository.setActiveBranchId("main")
            println("🌿 Branching: Created main branch")
        } else {
            println("🌿 Branching: Active branch: $activeBranchId")
        }
    }

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        val currentActiveBranchId = branchRepository.getActiveBranchId()
        activeBranchId = currentActiveBranchId

        val activePath = if (currentActiveBranchId != null) {
            chatRepository.getBranchPathWithCheckpoint(currentActiveBranchId)
        } else {
            messages
        }

        totalMessages = messages.size
        messagesInContext = activePath.size

        println("📊 Branching context:")
        println("  Active branch: $activeBranchId")
        println("  Total messages in DB: $totalMessages")
        println("  Messages in active path: $messagesInContext")

        activePath.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    override suspend fun onUserMessage(message: ChatMessage) {
        println("📥 Branching: User message received: ${message.content.take(50)}... (branch: ${message.branchId})")
    }

    override suspend fun onAssistantMessage(message: ChatMessage) {
        println("📤 Branching: Assistant message received: ${message.content.take(50)}... (branch: ${message.branchId})")
    }

    override suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        println("📥 Branching: Conversation pair received")
    }

    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "strategy" to "Branching",
            "activeBranchId" to (activeBranchId ?: "none"),
            "totalMessages" to totalMessages,
            "messagesInContext" to messagesInContext
        )
    }
}
