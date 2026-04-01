package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.data.local.entity.toEntity
import com.example.aiadventchallenge.data.repository.ChatRepository
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.MemoryConfig
import com.example.aiadventchallenge.domain.model.MemoryContext
import com.example.aiadventchallenge.domain.model.MemoryDebugInfo
import com.example.aiadventchallenge.domain.model.MemoryStats
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private fun createClassificationEntity(
    message: ChatMessage,
    metrics: com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics,
    isMultiple: Boolean = false
): com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity {
    val requestId = if (isMultiple) "req_${System.currentTimeMillis()}_${(0..9999).random()}" else null

    return com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity(
        id = "class_${System.currentTimeMillis()}_${(0..9999).random()}",
        userMessage = message.content,
        branchId = message.branchId,
        action = "create",
        memoryType = null,
        reason = null,
        importance = null,
        createdAt = System.currentTimeMillis(),
        executionTimeMs = metrics.executionTimeMs,
        promptTokens = metrics.promptTokens,
        completionTokens = metrics.completionTokens,
        totalTokens = metrics.totalTokens,
        isMultiple = isMultiple,
        requestId = requestId
    )
}

fun ClassificationResult.toEntity(
    message: ChatMessage,
    metrics: com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics,
    isMultiple: Boolean = false
): com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity {
    val action: String
    val memoryTypeStr: String?
    val reasonStr: String?
    val importanceVal: Float?

    when (this) {
        is ClassificationResult.Create -> {
            action = "create"
            memoryTypeStr = memoryType.name.lowercase()
            reasonStr = reason.name
            importanceVal = importance
        }
        is ClassificationResult.Skip -> {
            action = "skip"
            memoryTypeStr = null
            reasonStr = null
            importanceVal = null
        }
    }

    val requestId = if (isMultiple) "req_${System.currentTimeMillis()}_${(0..9999).random()}" else null

    return com.example.aiadventchallenge.data.local.entity.MemoryClassificationEntity(
        id = "class_${System.currentTimeMillis()}_${(0..9999).random()}",
        userMessage = message.content,
        branchId = message.branchId,
        action = action,
        memoryType = memoryTypeStr,
        reason = reasonStr,
        importance = importanceVal,
        createdAt = System.currentTimeMillis(),
        executionTimeMs = metrics.executionTimeMs,
        promptTokens = metrics.promptTokens,
        completionTokens = metrics.completionTokens,
        totalTokens = metrics.totalTokens,
        isMultiple = isMultiple,
        requestId = requestId
    )
}

class MemoryManager(
    private val memoryRepository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val consolidator: MemoryConsolidator,
    private val classificationRepository: MemoryClassificationRepository,
    private val shortTermWindow: Int = 10
) {

    suspend fun onConversationPair(userMessage: ChatMessage, assistantMessage: ChatMessage) {
        println("🧠 MemoryManager: Processing conversation pair...")
        println("   User: ${userMessage.content.take(100)}${if (userMessage.content.length > 100) "..." else ""}")
        println("   Assistant: ${assistantMessage.content.take(100)}${if (assistantMessage.content.length > 100) "..." else ""}")

        memoryRepository.deactivateExpiredEntries()

        val workingMemory = memoryRepository.getWorkingMemory(userMessage.branchId).first()
        val longTermMemory = memoryRepository.getLongTermMemory(userMessage.branchId).first()

        println("   Working memory size: ${workingMemory.size}")
        println("   Long-term memory size: ${longTermMemory.size}")

        val consolidationResult = consolidator.consolidateConversationPair(
            userMessage,
            assistantMessage,
            workingMemory,
            longTermMemory
        )

        if (consolidationResult.newTaskDetected) {
            println("🔄 MemoryManager: New task detected, clearing working memory for branch ${userMessage.branchId}")
            memoryRepository.clearWorkingMemory(userMessage.branchId)
        }

        consolidationResult.workingMemoryUpdates.forEach { result ->
            val entry = createMemoryEntry(result, userMessage, MemoryType.WORKING)
            memoryRepository.insertEntry(entry)
            println("✅ MemoryManager: Saved to working memory - ${entry.reason.name}: ${entry.value}")
        }

        consolidationResult.longTermUpdates.forEach { result ->
            val existing = memoryRepository.getEntryByKey(
                result.key ?: "fact_${result.value.hashCode()}",
                userMessage.branchId
            )

            if (existing == null) {
                val entry = createMemoryEntry(result, userMessage, MemoryType.LONG_TERM)
                memoryRepository.insertEntry(entry)
                println("✅ MemoryManager: Saved to long-term memory - ${entry.reason.name}: ${entry.value}")
            } else {
                println("⚠️ MemoryManager: Entry already exists - ${existing.key}")
            }
        }

        if (consolidationResult.workingMemoryUpdates.isNotEmpty() || consolidationResult.longTermUpdates.isNotEmpty()) {
            val classificationEntity = createClassificationEntity(
                userMessage,
                consolidationResult.metrics.llmMetrics,
                isMultiple = consolidationResult.workingMemoryUpdates.size + consolidationResult.longTermUpdates.size > 1
            )
            classificationRepository.saveClassificationMetrics(classificationEntity)
        }

        println("✅ MemoryManager: Conversation pair consolidation complete")
        println("   Working updates: ${consolidationResult.workingMemoryUpdates.size}")
        println("   Long-term updates: ${consolidationResult.longTermUpdates.size}")
        println("   Skipped: ${consolidationResult.skippedCount}")
    }

    private fun createMemoryEntry(
        result: ClassificationResult.Create,
        message: ChatMessage,
        memoryType: MemoryType
    ): MemoryEntry {
        val ttl = if (memoryType == MemoryType.WORKING) {
            System.currentTimeMillis() + 30 * 60 * 1000
        } else {
            null
        }

        return MemoryEntry(
            id = generateId(),
            key = result.key ?: "${memoryType.name.lowercase()}_${System.currentTimeMillis()}",
            value = result.value,
            memoryType = memoryType,
            reason = result.reason,
            source = result.source,
            importance = result.importance,
            branchId = message.branchId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isActive = true,
            ttl = ttl
        )
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
        return messages.takeLast(shortTermWindow)
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