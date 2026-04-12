package com.example.aiadventchallenge.domain.context

import android.util.Log
import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.context.FactExtractor
import kotlinx.coroutines.flow.first

class StickyFactsStrategy(
    private val config: ContextStrategyConfig,
    private val factRepository: FactRepository,
    private val factExtractor: FactExtractor
) : ContextStrategy {

    private var filteredMessagesCount: Int = 0

    init {
        require(config.windowSize > 0) { "windowSize must be positive, got ${config.windowSize}" }
    }

    override suspend fun buildContext(
        chatId: String?,
        messages: List<ChatMessage>,
        systemPrompt: String
    ): List<Message> {
        val result = mutableListOf<Message>()

        result.add(Message(MessageRole.SYSTEM, systemPrompt))

        val facts = factRepository.getAllFacts().first()
        if (facts.isNotEmpty()) {
            val factsText = facts.joinToString("\n") { fact ->
                "${fact.key}: ${fact.value}"
            }
            result.add(
                Message(
                    MessageRole.SYSTEM,
                    "Known facts about conversation:\n$factsText"
                )
            )
        }

        val windowMessages = messages.takeLast(config.windowSize)
        filteredMessagesCount = messages.size - windowMessages.size

        println("📝 StickyFacts context:")
        println("  Total messages: ${messages.size}")
        println("  Window size: ${config.windowSize}")
        println("  Messages in context: ${windowMessages.size}")
        println("  Messages filtered: $filteredMessagesCount")

        windowMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    override suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        try {
            val existingFacts = factRepository.getAllFacts().first()
            factExtractor.extractAndUpdateFacts(userMessage.content, existingFacts)
                .onSuccess { updatedFacts ->
                    factRepository.clearAllFacts()
                    updatedFacts.forEach { fact ->
                        factRepository.insertFact(fact)
                        println("✅ StickyFacts: Updated ${updatedFacts.size} facts from conversation pair")
                    }
                }
                .onFailure { error ->
                    println("❌ StickyFacts: Failed to extract facts - ${error.message}")
                }
        } catch (e: Exception) {
            println("❌ StickyFacts: Error updating facts - ${e.message}")
        }
    }

    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "strategy" to "StickyFacts",
            "windowSize" to config.windowSize,
            "filteredMessagesCount" to filteredMessagesCount,
            "messagesInContext" to config.windowSize
        )
    }
}
