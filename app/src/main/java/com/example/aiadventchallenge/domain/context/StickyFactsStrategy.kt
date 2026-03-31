package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.data.model.Message
import com.example.aiadventchallenge.data.model.MessageRole
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.FactEntry
import com.example.aiadventchallenge.domain.repository.FactRepository
import kotlinx.coroutines.flow.first

class StickyFactsStrategy(
    private val config: ContextStrategyConfig,
    private val factRepository: FactRepository,
    private val factExtractor: FactExtractor
) : ContextStrategy {

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
                    "Known facts about the conversation:\n$factsText"
                )
            )
        }

        val windowMessages = messages.takeLast(config.windowSize)
        windowMessages.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) MessageRole.USER else MessageRole.ASSISTANT
            result.add(Message(role, chatMessage.content))
        }

        return result
    }

    override suspend fun onUserMessage(message: ChatMessage) {
        try {
            val existingFacts = factRepository.getAllFacts().first()
            factExtractor.extractAndUpdateFacts(message.content, existingFacts)
                .onSuccess { updatedFacts ->
                    factRepository.clearAllFacts()
                    updatedFacts.forEach { fact ->
                        factRepository.insertFact(fact)
                    }
                    println("📝 StickyFacts: Updated ${updatedFacts.size} facts from user message")
                }
                .onFailure { error ->
                    println("❌ StickyFacts: Failed to extract facts - ${error.message}")
                }
        } catch (e: Exception) {
            println("❌ StickyFacts: Error updating facts - ${e.message}")
        }
    }

    override suspend fun onAssistantMessage(message: ChatMessage) {
        println("📤 StickyFacts: Assistant message received: ${message.content.take(50)}...")
    }

    override suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        println("📥 StickyFacts: Conversation pair received")
    }

    override fun getDebugInfo(): Map<String, Any> {
        return mapOf(
            "strategy" to "StickyFacts",
            "windowSize" to config.windowSize
        )
    }
}
