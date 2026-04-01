package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.MemoryConfig
import com.example.aiadventchallenge.domain.memory.AiMemoryClassifier
import com.example.aiadventchallenge.domain.memory.MemoryConsolidator
import com.example.aiadventchallenge.domain.memory.MemoryManager
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository

class MemoryBasedStrategy(
    private val config: ContextStrategyConfig,
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val aiClassifier: AiMemoryClassifier,
    private val classificationRepository: MemoryClassificationRepository
) : ContextStrategy {

    private val memoryConfig = config.memoryConfig ?: defaultMemoryConfig()
    
    private val consolidator = MemoryConsolidator(
        aiClassifier,
        memoryConfig
    )
    
    private val memoryManager = MemoryManager(
        memoryRepository,
        chatRepository,
        consolidator,
        classificationRepository
    )

    private var lastContext: com.example.aiadventchallenge.domain.model.MemoryContext? = null
    private var totalMessages: Int = 0
    private var messagesInContext: Int = 0

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()
        val branchId = messages.firstOrNull()?.branchId ?: "main"

        totalMessages = messages.size

        // 1. System Prompt
        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        // 2. Long-term memory (профиль, устойчивые предпочтения)
        val memoryContext = memoryManager.buildMemoryContext(branchId)
        lastContext = memoryContext

        if (memoryContext.longTermMemory.isNotEmpty()) {
            val longTermText = memoryContext.longTermMemory.joinToString("\n") { entry ->
                "- ${entry.reason.name.lowercase()}: ${entry.value}"
            }
            result.add(
                Message(
                    MessageRole.SYSTEM,
                    "About user:\n$longTermText"
                )
            )
        }

        // 3. Working memory (текущая задача)
        if (memoryContext.workingMemory.isNotEmpty()) {
            val workingText = memoryContext.workingMemory.joinToString("\n") { entry ->
                "- ${entry.reason.name.lowercase()}: ${entry.value}"
            }
            result.add(
                Message(
                    MessageRole.SYSTEM,
                    "Current task context:\n$workingText"
                )
            )
        }

        // 4. Short-term memory (последние сообщения)
        val shortTermMessages = memoryManager.getShortTermMessages(messages)
        shortTermMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        messagesInContext = result.size

        println("📊 MemoryBased context:")
        println("  Long-term entries: ${memoryContext.longTermMemory.size}")
        println("  Working entries: ${memoryContext.workingMemory.size}")
        println("  Short-term messages: ${shortTermMessages.size}")
        println("  Total messages in context: $messagesInContext")

        return result
    }

    override suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        memoryManager.onConversationPair(userMessage, assistantMessage)
    }

    override fun getDebugInfo(): Map<String, Any> {
        val lastContext = lastContext
        return mapOf(
            "strategy" to "MemoryBased (AI)",
            "longTermMemoryCount" to (lastContext?.longTermMemory?.size ?: 0),
            "workingMemoryCount" to (lastContext?.workingMemory?.size ?: 0),
            "shortTermWindow" to memoryConfig.shortTermWindow,
            "totalMessages" to totalMessages,
            "messagesInContext" to messagesInContext
        )
    }

    suspend fun getMemoryDebugInfo(branchId: String, allMessages: List<ChatMessage>): com.example.aiadventchallenge.domain.model.MemoryDebugInfo {
        return memoryManager.getDebugInfo(branchId, allMessages)
    }

    private fun defaultMemoryConfig() = MemoryConfig(
        shortTermWindow = config.windowSize,
        workingMemoryTTL = 30 * 60 * 1000,
        longTermImportanceThreshold = 0.7f
    )
}