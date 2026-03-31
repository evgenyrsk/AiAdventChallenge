package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.data.local.entity.toEntity
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics
import com.example.aiadventchallenge.domain.model.MemoryConfig
import com.example.aiadventchallenge.domain.model.MemoryContext
import com.example.aiadventchallenge.domain.model.MemoryDebugInfo
import com.example.aiadventchallenge.domain.model.MemoryStats
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MemoryManager(
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val aiClassifier: AiMemoryClassifier,
    private val config: MemoryConfig,
    private val classificationRepository: MemoryClassificationRepository
) {

    suspend fun onUserMessage(message: ChatMessage) {
        println("🧠 MemoryManager: Processing user message...")

        memoryRepository.deactivateExpiredEntries()

        val workingMemory = memoryRepository.getWorkingMemory(message.branchId).first()
        val longTermMemory = memoryRepository.getLongTermMemory(message.branchId).first()

        val result = aiClassifier.classifyUserMessage(message, workingMemory, longTermMemory)
        if (result.isSuccess) {
            val multiResult = result.getOrNull()!!

            multiResult.results.forEach { classificationResult ->
                processClassificationResult(classificationResult, message)
            }

            if (multiResult.results.isNotEmpty()) {
                val classificationEntity = multiResult.results.first().toEntity(
                    message, multiResult.metrics, isMultiple = multiResult.results.size > 1
                )
                classificationRepository.saveClassificationMetrics(classificationEntity)
            }
        } else {
            println("❌ MemoryManager: Failed to classify message - ${result.exceptionOrNull()?.message}")
            println("   Message: ${message.content}")
        }
    }

    suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        println("🧠 MemoryManager: Processing conversation pair...")
        println("   User: ${userMessage.content.take(100)}${if (userMessage.content.length > 100) "..." else ""}")
        println("   Assistant: ${assistantMessage.content.take(100)}${if (assistantMessage.content.length > 100) "..." else ""}")

        memoryRepository.deactivateExpiredEntries()

        val workingMemory = memoryRepository.getWorkingMemory(userMessage.branchId).first()
        val longTermMemory = memoryRepository.getLongTermMemory(userMessage.branchId).first()

        println("   Working memory size: ${workingMemory.size}")
        println("   Long-term memory size: ${longTermMemory.size}")

        val result = aiClassifier.classifyConversationPair(userMessage, assistantMessage, workingMemory, longTermMemory)
        if (result.isSuccess) {
            val pairClassification = result.getOrNull()!!

            pairClassification.userResults.forEach { classificationResult ->
                processClassificationResult(classificationResult, userMessage)
            }

            pairClassification.assistantResults.forEach { classificationResult ->
                processClassificationResult(classificationResult, assistantMessage)
            }

            println("✅ MemoryManager: Successfully processed conversation pair")
            println("   User classifications: ${pairClassification.userResults.size}")
            println("   Assistant classifications: ${pairClassification.assistantResults.size}")

            if (pairClassification.userResults.isNotEmpty()) {
                val classificationEntity = pairClassification.userResults.first().toEntity(
                    userMessage, pairClassification.metrics, isMultiple = true
                )
                classificationRepository.saveClassificationMetrics(classificationEntity)
            }
        } else {
            println("❌ MemoryManager: Failed to classify conversation pair")
            println("   Error: ${result.exceptionOrNull()?.message}")
            println("   User message length: ${userMessage.content.length}")
            println("   Assistant message length: ${assistantMessage.content.length}")
            result.exceptionOrNull()?.printStackTrace()
        }
    }

    private suspend fun processClassificationResult(
        classificationResult: ClassificationResult,
        message: ChatMessage
    ) {
        when (classificationResult) {
            is ClassificationResult.Create -> {
                val sourceValue = classificationResult.source
                val importanceValue = classificationResult.importance

                when (classificationResult.memoryType) {
                    MemoryType.WORKING -> {
                        val entry = MemoryEntry(
                            id = generateId(),
                            key = classificationResult.key ?: "task_${System.currentTimeMillis()}",
                            value = classificationResult.value,
                            memoryType = MemoryType.WORKING,
                            reason = classificationResult.reason,
                            source = sourceValue,
                            importance = importanceValue,
                            branchId = message.branchId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            isActive = true,
                            ttl = System.currentTimeMillis() + config.workingMemoryTTL
                        )
                        memoryRepository.insertEntry(entry)
                        println("✅ MemoryManager: Saved to working memory - ${entry.reason.name}: ${entry.value}")
                    }
                    MemoryType.LONG_TERM -> {
                        if (importanceValue >= config.longTermImportanceThreshold) {
                            val existing = memoryRepository.getEntryByKey(
                                classificationResult.key ?: "fact_${classificationResult.value.hashCode()}",
                                message.branchId
                            )

                            if (existing == null) {
                                val entry = MemoryEntry(
                                    id = generateId(),
                                    key = classificationResult.key ?: "fact_${classificationResult.value.hashCode()}",
                                    value = classificationResult.value,
                                    memoryType = MemoryType.LONG_TERM,
                                    reason = classificationResult.reason,
                                    source = sourceValue,
                                    importance = importanceValue,
                                    branchId = message.branchId,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    isActive = true,
                                    ttl = null
                                )
                                memoryRepository.insertEntry(entry)
                                println("✅ MemoryManager: Saved to long-term memory - ${entry.reason.name}: ${entry.value}")
                            } else {
                                println("⚠️ MemoryManager: Entry already exists - ${existing.key}")
                            }
                        } else {
                            println("⏭️ MemoryManager: Importance $importanceValue < threshold ${config.longTermImportanceThreshold}, skipping long-term save")
                        }
                    }
                    MemoryType.SHORT_TERM -> {
                        println("ℹ️ MemoryManager: Short-term message handled in context building")
                    }
                }
            }
            is ClassificationResult.Skip -> {
                println("⏭️ MemoryManager: Classification result - SKIP")
            }
        }
    }

    suspend fun onAssistantMessage(message: ChatMessage) {
        println("🧠 MemoryManager: Processing assistant message...")

        val workingMemory = memoryRepository.getWorkingMemory(message.branchId).first()

        val result = aiClassifier.classifyAssistantMessage(message, workingMemory)
        if (result.isSuccess) {
            val multiResult = result.getOrNull()!!

            multiResult.results.forEach { classificationResult ->
                processClassificationResult(classificationResult, message)
            }

            if (multiResult.results.isNotEmpty()) {
                val classificationEntity = multiResult.results.first().toEntity(
                    message, multiResult.metrics, isMultiple = multiResult.results.size > 1
                )
                classificationRepository.saveClassificationMetrics(classificationEntity)
            }

            println("📊 MemoryManager: Processed ${multiResult.results.size} classifications")
            println("   Tokens: ${multiResult.metrics.promptTokens?.plus(multiResult.metrics.completionTokens ?: 0) ?: 0}")
        } else {
            println("❌ MemoryManager: Failed to classify assistant message - ${result.exceptionOrNull()?.message}")
        }
    }

    suspend fun buildMemoryContext(branchId: String): MemoryContext {
        val workingMemory = memoryRepository.getWorkingMemory(branchId).first()
        val longTermMemory = memoryRepository.getLongTermMemory(branchId).first()

        return MemoryContext(
            workingMemory = workingMemory,
            longTermMemory = longTermMemory.sortedByDescending { it.importance }
        )
    }

    fun getShortTermMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages.takeLast(config.shortTermWindow)
    }

    suspend fun getDebugInfo(branchId: String, allMessages: List<ChatMessage>): MemoryDebugInfo {
        val workingMemory = memoryRepository.getWorkingMemory(branchId).first()
        val longTermMemory = memoryRepository.getLongTermMemory(branchId).first()
        val allMemoryEntries = memoryRepository.getAllEntriesByBranch(branchId).first()

        val shortTermMessages = getShortTermMessages(allMessages)

        val assembledContext = mutableListOf<String>()
        if (longTermMemory.isNotEmpty()) {
            assembledContext.add("User Profile:\n${longTermMemory.joinToString("\n") { "- ${it.reason.name.lowercase()}: ${it.value}" }}")
        }
        if (workingMemory.isNotEmpty()) {
            assembledContext.add("Current Task:\n${workingMemory.joinToString("\n") { "- ${it.reason.name.lowercase()}: ${it.value}" }}")
        }

        return MemoryDebugInfo(
            shortTermMessages = shortTermMessages,
            workingMemory = workingMemory,
            longTermMemory = longTermMemory,
            assembledContext = assembledContext,
            memoryStats = MemoryStats(
                totalWorkingEntries = allMemoryEntries.count { it.memoryType == MemoryType.WORKING },
                activeWorkingEntries = workingMemory.size,
                totalLongTermEntries = allMemoryEntries.count { it.memoryType == MemoryType.LONG_TERM },
                activeLongTermEntries = longTermMemory.size
            )
        )
    }

    suspend fun clearTask(branchId: String) {
        println("🧹 MemoryManager: Clearing working memory for branch: $branchId")
        memoryRepository.clearWorkingMemory(branchId)
        classificationRepository.clearClassifications(branchId)
    }

    suspend fun clearProfile(branchId: String) {
        println("🧹 MemoryManager: Clearing long-term memory for branch: $branchId")
        memoryRepository.clearLongTermMemory(branchId)
    }

    suspend fun clearAll(branchId: String) {
        println("🧹 MemoryManager: Clearing all memory for branch: $branchId")
        memoryRepository.clearAllMemory(branchId)
        classificationRepository.clearClassifications(branchId)
    }

    private fun generateId(): String = "mem_${System.currentTimeMillis()}_${(0..9999).random()}"
}