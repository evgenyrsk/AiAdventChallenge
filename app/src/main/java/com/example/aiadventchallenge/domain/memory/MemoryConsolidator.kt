package com.example.aiadventchallenge.domain.memory

import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.MemoryConfig
import com.example.aiadventchallenge.domain.model.MemoryClassificationMetrics

data class ConsolidationResult(
    val workingMemoryUpdates: List<ClassificationResult.Create>,
    val longTermUpdates: List<ClassificationResult.Create>,
    val skippedCount: Int,
    val newTaskDetected: Boolean,
    val metrics: ConsolidationMetrics
)

data class ConsolidationMetrics(
    val userClassifications: Int,
    val assistantClassifications: Int,
    val workingUpdates: Int,
    val longTermUpdates: Int,
    val skippedCount: Int,
    val llmMetrics: MemoryClassificationMetrics
)

class MemoryConsolidator(
    private val aiClassifier: AiMemoryClassifier,
    private val config: MemoryConfig
) {

    suspend fun consolidateConversationPair(
        userMessage: ChatMessage,
        assistantMessage: ChatMessage,
        currentWorkingMemory: List<MemoryEntry>,
        currentLongTermMemory: List<MemoryEntry>
    ): ConsolidationResult {
        val pairResult = aiClassifier.classifyConversationPair(
            userMessage,
            assistantMessage,
            currentWorkingMemory,
            currentLongTermMemory
        )

        if (pairResult.isFailure) {
            return ConsolidationResult(
                workingMemoryUpdates = emptyList(),
                longTermUpdates = emptyList(),
                skippedCount = 0,
                newTaskDetected = false,
                metrics = ConsolidationMetrics(
                    userClassifications = 0,
                    assistantClassifications = 0,
                    workingUpdates = 0,
                    longTermUpdates = 0,
                    skippedCount = 0,
                    llmMetrics = MemoryClassificationMetrics(
                        promptTokens = null,
                        completionTokens = null,
                        totalTokens = null,
                        executionTimeMs = 0
                    )
                )
            )
        }

        val multiResult = pairResult.getOrThrow()
        
        val allResults = multiResult.userResults + multiResult.assistantResults
        val createResults = allResults.filterIsInstance<ClassificationResult.Create>()
        
        val workingUpdates = createResults
            .filter { it.memoryType == MemoryType.WORKING }
            .filter { shouldUpdateWorkingMemory(it) }
        
        val longTermUpdates = createResults
            .filter { it.memoryType == MemoryType.LONG_TERM }
            .filter { shouldUpdateLongTermMemory(it) }
        
        val skippedCount = allResults.size - createResults.size + 
            (createResults.size - workingUpdates.size - longTermUpdates.size)

        val metrics = ConsolidationMetrics(
            userClassifications = multiResult.userResults.size,
            assistantClassifications = multiResult.assistantResults.size,
            workingUpdates = workingUpdates.size,
            longTermUpdates = longTermUpdates.size,
            skippedCount = skippedCount,
            llmMetrics = multiResult.metrics
        )

        println("📊 MemoryConsolidator: Consolidation complete")
        println("   User classifications: ${metrics.userClassifications}")
        println("   Assistant classifications: ${metrics.assistantClassifications}")
        println("   Working memory updates: ${metrics.workingUpdates}")
        println("   Long-term updates: ${metrics.longTermUpdates}")
        println("   Skipped: ${metrics.skippedCount}")
        println("   New task detected: ${multiResult.newTaskDetected}")
        println("   Tokens: ${metrics.llmMetrics.promptTokens?.plus(metrics.llmMetrics.completionTokens ?: 0) ?: 0}")

        return ConsolidationResult(
            workingMemoryUpdates = workingUpdates,
            longTermUpdates = longTermUpdates,
            skippedCount = skippedCount,
            newTaskDetected = multiResult.newTaskDetected,
            metrics = metrics
        )
    }

    private fun shouldUpdateWorkingMemory(result: ClassificationResult.Create): Boolean {
        return result.memoryType == MemoryType.WORKING
    }

    private fun shouldUpdateLongTermMemory(result: ClassificationResult.Create): Boolean {
        return result.memoryType == MemoryType.LONG_TERM && 
            result.importance >= config.longTermImportanceThreshold
    }
}